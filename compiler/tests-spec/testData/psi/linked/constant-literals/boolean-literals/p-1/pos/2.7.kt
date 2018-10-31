/*
 * KOTLIN PSI SPEC TEST (POSITIVE)
 *
 * SECTIONS: constant-literals, boolean-literals
 * PARAGRAPH: 1
 * SENTENCE: [2] These are strong keywords which cannot be used as identifiers unless escaped.
 * NUMBER: 7
 * DESCRIPTION: The use of Boolean literals as the identifier (with backtick) in the enumEntry.
 * NOTE: this test data is generated by FeatureInteractionTestDataGenerator. DO NOT MODIFY CODE MANUALLY!
 */

enum class A(x1: Any?) {
    `true`("1"),
    `false`("2");
}
