// !WITH_ENUM_CLASSES

/*
 * KOTLIN DIAGNOSTICS SPEC TEST (POSITIVE)
 *
 * SECTIONS: when-expression
 * PARAGRAPH: 11
 * SENTENCE: [7] The bound expression is of an Enum classes type and all enumerated values are checked for equality using constant conditions;
 * NUMBER: 1
 * DESCRIPTION: Check when exhaustive when all enumerated values are checked.
 */

// TESTCASE NUMBER: 1
fun case_1(dir: _EnumClass): String = when (dir) {
    _EnumClass.EAST -> ""
    _EnumClass.NORTH -> ""
    _EnumClass.SOUTH -> ""
    _EnumClass.WEST -> ""
}

// TESTCASE NUMBER: 2
fun case_2(value_1: _EnumClassSingle): String = when (value_1) {
    _EnumClassSingle.EVERYTHING -> ""
}
