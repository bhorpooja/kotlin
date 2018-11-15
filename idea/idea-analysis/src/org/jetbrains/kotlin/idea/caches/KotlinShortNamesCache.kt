/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.caches

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.Processor
import com.intellij.util.Processors
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.indexing.IdFilter
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.defaultImplsChild
import org.jetbrains.kotlin.asJava.finder.JavaElementFinder
import org.jetbrains.kotlin.asJava.getAccessorLightMethods
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.idea.decompiler.builtIns.KotlinBuiltInFileType
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.load.java.getPropertyNamesCandidatesByAccessorName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.sequenceOfLazyValues

class KotlinShortNamesCache(private val project: Project) : PsiShortNamesCache() {
    /**
     * Return kotlin class names from project sources which should be visible from java.
     */
    override fun getAllClassNames(): Array<String> {
        return CancelableArrayCollectProcessor<String>().also { processor ->
            processAllClassNames(processor)
        }.toArray(arrayOf())
    }

    override fun processAllClassNames(processor: Processor<String>): Boolean {
        return KotlinClassShortNameIndex.getInstance().processAllKeys(project, processor) &&
                KotlinFileFacadeShortNameIndex.INSTANCE.processAllKeys(project, processor)
    }

    /**
     * Return class names form kotlin sources in given scope which should be visible as Java classes.
     */
    override fun getClassesByName(name: String, scope: GlobalSearchScope): Array<PsiClass> {
        return CancelableArrayCollectProcessor<PsiClass>().also { processor ->
            processClassesWithName(name, processor, scope, null)
        }.toArray(arrayOf())
    }

    override fun processClassesWithName(
        name: String,
        processor: Processor<in PsiClass>,
        scope: GlobalSearchScope,
        filter: IdFilter?
    ): Boolean {
        val effectiveScope = kotlinDeclarationsVisibleFromJavaScope(scope)
        val fqNameProcessor = Processor<FqName> { fqName: FqName? ->
            if (fqName == null) return@Processor true

            val isInterfaceDefaultImpl = name == JvmAbi.DEFAULT_IMPLS_CLASS_NAME && fqName.shortName().asString() != name
            assert(fqName.shortName().asString() == name || isInterfaceDefaultImpl) {
                "A declaration obtained from index has non-matching name:\nin index: $name\ndeclared: ${fqName.shortName()}($fqName)"
            }

            val fqNameToSearch = if (isInterfaceDefaultImpl) fqName.defaultImplsChild() else fqName

            val psiClass = JavaElementFinder.getInstance(project).findClass(fqNameToSearch.asString(), effectiveScope)
            if (psiClass == null) {
                return@Processor true
            }

            return@Processor processor.process(psiClass)
        }

        val allKtClassOrObjectsProcessed = StubIndex.getInstance().processElements(
            KotlinClassShortNameIndex.getInstance().key,
            name,
            project,
            scope,
            filter,
            KtClassOrObject::class.java
        ) { ktClassOrObject ->
            fqNameProcessor.process(ktClassOrObject.fqName)
        }
        if (!allKtClassOrObjectsProcessed) {
            return false
        }

        return StubIndex.getInstance().processElements(
            KotlinFileFacadeShortNameIndex.getInstance().key,
            name,
            project,
            scope,
            filter,
            KtFile::class.java
        ) { ktFile ->
            fqNameProcessor.process(ktFile.javaFileFacadeFqName)
        }
    }

    private fun kotlinDeclarationsVisibleFromJavaScope(scope: GlobalSearchScope): GlobalSearchScope {
        val noBuiltInsScope: GlobalSearchScope = object : GlobalSearchScope(project) {
            override fun isSearchInModuleContent(aModule: Module) = true
            override fun compare(file1: VirtualFile, file2: VirtualFile) = 0
            override fun isSearchInLibraries() = true
            override fun contains(file: VirtualFile) = file.fileType != KotlinBuiltInFileType
        }
        return KotlinSourceFilterScope.sourceAndClassFiles(scope, project).intersectWith(noBuiltInsScope)
    }

    override fun getMethodsByName(name: String, scope: GlobalSearchScope): Array<PsiMethod> {
        return getMethodSequenceByName(name, scope).toList().toTypedArray()
    }

    private fun getMethodSequenceByName(name: String, scope: GlobalSearchScope): Sequence<PsiMethod> {
        val propertiesIndex = KotlinPropertyShortNameIndex.getInstance()
        val functionIndex = KotlinFunctionShortNameIndex.getInstance()

        val kotlinFunctionsPsi = functionIndex.get(name, project, scope).asSequence()
            .flatMap { LightClassUtil.getLightClassMethods(it).asSequence() }
            .filter { it.name == name }

        val propertyAccessorsPsi = sequenceOfLazyValues({ getPropertyNamesCandidatesByAccessorName(Name.identifier(name)) })
            .flatMap { it.asSequence() }
            .flatMap { propertiesIndex.get(it.asString(), project, scope).asSequence() }
            .flatMap { it.getAccessorLightMethods().allDeclarations.asSequence() }
            .filter { it.name == name }
            .map { it as? PsiMethod }

        return sequenceOfLazyValues({ kotlinFunctionsPsi }, { propertyAccessorsPsi }).flatMap { it }.filterNotNull()
    }

    override fun getMethodsByNameIfNotMoreThan(name: String, scope: GlobalSearchScope, maxCount: Int): Array<PsiMethod> {
        require(maxCount >= 0)
        val psiMethods = getMethodSequenceByName(name, scope)
        val limitedByMaxCount = psiMethods.take(maxCount).toList()
        if (limitedByMaxCount.size == 0)
            return PsiMethod.EMPTY_ARRAY
        return limitedByMaxCount.toTypedArray()
    }

    override fun getFieldsByNameIfNotMoreThan(name: String, scope: GlobalSearchScope, maxCount: Int): Array<PsiField> {
        require(maxCount >= 0)
        val psiFields = getFieldSequenceByName(name, scope)
        val limitedByMaxCount = psiFields.take(maxCount).toList()
        if (limitedByMaxCount.size == 0)
            return PsiField.EMPTY_ARRAY
        return limitedByMaxCount.toTypedArray()
    }

    override fun processMethodsWithName(name: String, scope: GlobalSearchScope, processor: Processor<PsiMethod>): Boolean =
        ContainerUtil.process(getMethodsByName(name, scope), processor)

    override fun getAllMethodNames(): Array<String> {
        return CancelableArrayCollectProcessor<String>().also { processor ->
            processAllMethodNames(processor)
        }.toArray(arrayOf())
    }

    override fun processAllMethodNames(processor: Processor<String>, scope: GlobalSearchScope, filter: IdFilter?): Boolean {
        return processAllMethodNames(processor)
    }

    private fun processAllMethodNames(processor: Processor<String>): Boolean {
        if (!KotlinFunctionShortNameIndex.getInstance().processAllKeys(project, processor)) {
            return false
        }

        return KotlinPropertyShortNameIndex.getInstance().processAllKeys(project) { name ->
            return@processAllKeys processor.process(JvmAbi.setterName(name)) && processor.process(JvmAbi.getterName(name))
        }
    }

    private fun getFieldSequenceByName(name: String, scope: GlobalSearchScope): Sequence<PsiField> {
        return KotlinPropertyShortNameIndex.getInstance().get(name, project, scope).asSequence()
            .map { LightClassUtil.getLightClassBackingField(it) }
            .filterNotNull()
    }

    override fun getFieldsByName(name: String, scope: GlobalSearchScope): Array<PsiField> {
        return getFieldSequenceByName(name, scope).toList().toTypedArray()
    }

    override fun getAllFieldNames(): Array<String> {
        return CancelableArrayCollectProcessor<String>().also { processor ->
            processAllFieldNames(processor)
        }.toArray(arrayOf())
    }

    override fun processAllFieldNames(processor: Processor<String>, scope: GlobalSearchScope, filter: IdFilter?): Boolean {
        return processAllFieldNames(processor)
    }

    private fun processAllFieldNames(processor: Processor<String>): Boolean {
        return KotlinPropertyShortNameIndex.getInstance().processAllKeys(project, processor)
    }

    private class CancelableArrayCollectProcessor<T> : Processor<T> {
        val troveSet = ContainerUtil.newTroveSet<T>()
        private val processor = Processors.cancelableCollectProcessor<T>(troveSet)

        override fun process(value: T): Boolean {
            return processor.process(value)
        }

        fun toArray(a: Array<T>): Array<T> = troveSet.toArray(a)
    }
}
