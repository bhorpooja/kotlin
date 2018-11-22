// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_PARAMETER

fun test(numbers: List<Number>) {
    shortResult { getShort() ?: 1 }
}

fun <R : Comparable<R>> shortResult(f: () -> R) {}

fun getShort(): Short? = 1