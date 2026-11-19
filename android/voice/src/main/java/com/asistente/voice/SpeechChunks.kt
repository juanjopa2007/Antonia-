package com.asistente.voice

private val SENTENCE_BREAK = Regex("(?<=[.!?…])\\s+")

/**
 * Divide un texto largo en trozos de como mucho [maxLength] caracteres, cortando preferentemente
 * entre frases. El motor de voz rechaza textos demasiado largos y, además, empezar por un trozo
 * corto hace que Antonia empiece a hablar antes.
 */
fun splitForSpeech(text: String, maxLength: Int): List<String> {
    require(maxLength >= 1) { "maxLength debe ser >= 1" }
    val clean = text.trim()
    if (clean.isEmpty()) return emptyList()
    if (clean.length <= maxLength) return listOf(clean)

    val chunks = mutableListOf<String>()
    val current = StringBuilder()

    fun flush() {
        if (current.isNotEmpty()) {
            chunks += current.toString().trim()
            current.clear()
        }
    }

    for (sentence in clean.split(SENTENCE_BREAK)) {
        if (sentence.length > maxLength) {
            flush()
            chunks += hardSplit(sentence, maxLength)
            continue
        }
        if (current.length + sentence.length + 1 > maxLength) flush()
        if (current.isNotEmpty()) current.append(' ')
        current.append(sentence)
    }
    flush()
    return chunks.filter { it.isNotEmpty() }
}

private fun hardSplit(sentence: String, maxLength: Int): List<String> {
    val parts = mutableListOf<String>()
    var rest = sentence.trim()
    while (rest.length > maxLength) {
        var cut = rest.lastIndexOf(' ', maxLength)
        if (cut <= 0) cut = maxLength
        parts += rest.substring(0, cut).trim()
        rest = rest.substring(cut).trim()
    }
    if (rest.isNotEmpty()) parts += rest
    return parts
}
