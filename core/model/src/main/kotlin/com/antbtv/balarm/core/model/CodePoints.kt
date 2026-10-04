package com.antbtv.balarm.core.model

/** Первые [n] code points строки; суррогатную пару (эмодзи) не разрезает, в отличие от [String.take]. */
fun String.takeCodePoints(n: Int): String {
    require(n >= 0) { "n must not be negative, was $n" }
    return if (codePointCount(0, length) <= n) this else substring(0, offsetByCodePoints(0, n))
}
