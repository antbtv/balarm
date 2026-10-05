package com.antbtv.balarm.core.designsystem.component

/** Символы перевода строки, которые в метке становятся пробелом (CRLF — один пробел): LF, CR, NEL, LS, PS. */
private const val LINE_BREAK_CHARS = "\n\r\u0085\u2028\u2029"

private const val SPACE = ' '

/**
 * Длина метки в code points (ADR-011 §7): эмодзи вне BMP — один символ, а не два `Char`.
 * Одиночный суррогат тоже считается за один.
 */
fun labelLength(text: String): Int = text.codePointCount(0, text.length)

/**
 * Результат правки метки.
 *
 * @property text принятый текст.
 * @property cursor позиция курсора (в `Char`) после принятого фрагмента; `null` — ввод принят как есть,
 * выделение и composition от IME сохраняются.
 */
internal data class LabelEdit(val text: String, val cursor: Int?)

/**
 * Правка поля метки: [previous] → [input]. Переводы строк — пробелы. При превышении [maxLength] code points
 * обрезается вставленный фрагмент, а не хвост метки: набор в заполненном поле отклоняется (текст = [previous]),
 * вставка в середину не съедает конец. Вставленный фрагмент — разница между общими началом и концом двух строк;
 * ни обрезка, ни границы не делят суррогатную пару. Пробелы по краям не трогаются — их обрезает сохранение.
 *
 * Локальная копия `takeCodePoints` из `:core:model`: дизайн-система от модели не зависит.
 */
internal fun sanitizeLabelEdit(previous: String, input: String, maxLength: Int): LabelEdit {
    require(maxLength >= 0) { "maxLength must be non-negative: $maxLength" }
    val text = toSingleLine(input)
    if (text == input && labelLength(text) <= maxLength) return LabelEdit(input, cursor = null)
    val prefix = commonPrefixLength(previous, text)
    val suffix = commonSuffixLength(previous, text, limit = minOf(previous.length, text.length) - prefix)
    val head = text.substring(0, prefix)
    val tail = text.substring(text.length - suffix)
    val room = maxLength - labelLength(head) - labelLength(tail)
    return if (room < 0) {
        // Прежний текст сам длиннее лимита (пришёл снаружи) — обычная обрезка с конца.
        val cut = text.takeCodePoints(maxLength)
        LabelEdit(cut, cursor = cut.length)
    } else {
        val accepted = text.substring(prefix, text.length - suffix).takeCodePoints(room)
        LabelEdit(head + accepted + tail, cursor = head.length + accepted.length)
    }
}

private fun toSingleLine(input: String): String {
    if (input.none { it in LINE_BREAK_CHARS }) return input
    return buildString(input.length) {
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c in LINE_BREAK_CHARS) {
                append(SPACE)
                if (c == '\r' && input.getOrNull(i + 1) == '\n') i++
            } else {
                append(c)
            }
            i++
        }
    }
}

private fun String.takeCodePoints(n: Int): String =
    if (labelLength(this) <= n) this else substring(0, offsetByCodePoints(0, n))

/** Длина общего начала, не заканчивающаяся посреди суррогатной пары. */
private fun commonPrefixLength(a: String, b: String): Int {
    val n = a.commonPrefixWith(b).length
    return if (b.splitsPairAt(n)) n - 1 else n
}

/** Длина общего конца (не больше [limit]), не начинающаяся посреди суррогатной пары. */
private fun commonSuffixLength(a: String, b: String, limit: Int): Int {
    var n = 0
    while (n < limit && a[a.length - 1 - n] == b[b.length - 1 - n]) n++
    return if (b.splitsPairAt(b.length - n)) n - 1 else n
}

/** Граница [index] проходит между старшим и младшим суррогатом одной пары. */
private fun String.splitsPairAt(index: Int): Boolean =
    getOrNull(index - 1)?.isHighSurrogate() == true && getOrNull(index)?.isLowSurrogate() == true
