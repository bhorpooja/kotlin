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

package org.jetbrains.kotlin.types

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.resolve.scopes.TypeIntersectionScope

import java.util.*

class IntersectionTypeConstructor(typesToIntersect: Collection<KotlinType>) : TypeConstructor {
    private val intersectedTypes: Set<KotlinType>?
    private val hashCode: Int

    init {
        assert(!typesToIntersect.isEmpty()) { "Attempt to create an empty intersection" }

        this.intersectedTypes = LinkedHashSet(typesToIntersect)
        this.hashCode = intersectedTypes.hashCode()
    }

    override fun getParameters(): List<TypeParameterDescriptor> {
        return emptyList()
    }

    override fun getSupertypes(): Collection<KotlinType> {
        return intersectedTypes
    }

    fun createScopeForKotlinType(): MemberScope {
        return TypeIntersectionScope.create("member scope for intersection type $this", intersectedTypes!!)
    }

    override fun isFinal(): Boolean {
        return false
    }

    override fun isDenotable(): Boolean {
        return false
    }

    override fun getDeclarationDescriptor(): ClassifierDescriptor? {
        return null
    }

    override fun getBuiltIns(): KotlinBuiltIns {
        return intersectedTypes!!.iterator().next().constructor.builtIns
    }

    override fun toString(): String {
        return makeDebugNameForIntersectionType(intersectedTypes!!)
    }

    private fun makeDebugNameForIntersectionType(resultingTypes: Iterable<KotlinType>): String {
        val debugName = StringBuilder("{")
        val iterator = resultingTypes.iterator()
        while (iterator.hasNext()) {
            val type = iterator.next()

            debugName.append(type.toString())
            if (iterator.hasNext()) {
                debugName.append(" & ")
            }
        }
        debugName.append("}")
        return debugName.toString()
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false

        val that = o as IntersectionTypeConstructor?

        return if (if (intersectedTypes != null) intersectedTypes != that!!.intersectedTypes else that!!.intersectedTypes != null) false else true

    }

    override fun hashCode(): Int {
        return hashCode
    }
}
