package com.aidriy.app.domain.markdown

/**
 * Keeps "Rapikan Format" honest: the AI may only change formatting.
 *
 * Before sending, image lines and berkas:// links are swapped for placeholders the AI must keep
 * ([protect]); afterwards they are put back ([restore]) and the words of both versions are
 * compared ([check]). Case, punctuation and Markdown markers may change; words may not.
 */
object FormatGuard {

    data class Protected(val text: String, val tokens: Map<String, String>)

    sealed interface Result {
        data class Ok(val text: String) : Result

        /** Placeholders the AI dropped or duplicated; the result cannot be restored safely. */
        data class LostBlocks(val missing: Int) : Result

        /** Words that differ (up to a few of each, for the message). */
        data class TextChanged(
            val removed: List<String>,
            val added: List<String>,
            val restored: String,
        ) : Result
    }

    private val berkasLink = Regex("""!?\[[^\]\n]*]\(\s*berkas://[^)\s]+(?:\s+"[^"]*")?\s*\)""")
    private val tokenRe = Regex("""⟦(GAMBAR|BERKAS)_(\d+)⟧""")

    fun protect(markdown: String): Protected {
        val tokens = linkedMapOf<String, String>()
        var image = 0
        var file = 0
        val text = berkasLink.replace(markdown) { m ->
            val key = if (m.value.startsWith("!")) "⟦GAMBAR_${++image}⟧" else "⟦BERKAS_${++file}⟧"
            tokens[key] = m.value
            key
        }
        return Protected(text, tokens)
    }

    /** Removes a ```markdown fence the model may wrap the answer in. */
    fun unwrap(answer: String): String {
        val t = answer.trim()
        val fence = Regex("""^```[\w-]*\n([\s\S]*?)\n```$""").find(t)
        return (fence?.groupValues?.get(1) ?: t).trim('\n')
    }

    fun check(original: String, protected: Protected, aiAnswer: String): Result {
        val answer = unwrap(aiAnswer)
        val found = tokenRe.findAll(answer).map { it.value }.toList()
        val missing = protected.tokens.keys.count { key -> found.count { it == key } != 1 }
        if (missing > 0 || found.any { it !in protected.tokens }) {
            return Result.LostBlocks(missing.coerceAtLeast(1))
        }
        val restored = tokenRe.replace(answer) { protected.tokens.getValue(it.value) }
        val before = words(original)
        val after = words(restored)
        if (before == after && taskStates(original) == taskStates(restored)) return Result.Ok(restored)
        return Result.TextChanged(
            removed = multisetMinus(before, after).take(MAX_LISTED),
            added = multisetMinus(after, before).take(MAX_LISTED),
            restored = restored,
        )
    }

    /** Words in reading order, ignoring case, punctuation, list numbers and Markdown markers. */
    fun words(markdown: String): List<String> {
        val out = mutableListOf<String>()
        markdown.replace("\r\n", "\n").split('\n').forEach { raw ->
            var line = raw.trim()
            if (Regex("""^\s*\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)*\|?\s*$""").matches(line) && line.contains('-')) return@forEach
            if (Regex("""^(```|~~~)""").containsMatchIn(line)) line = line.drop(3)
            line = line.replace(Regex("""^(>\s*)+"""), "")
                .replace(Regex("""^\[![A-Za-z]+]"""), "")
                .replace(Regex("""^#{1,6}\s+"""), "")
                .replace(Regex("""^([-*+]|\d{1,9}[.)])\s+(\[[ xX]]\s+)?"""), "")
            line.split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }.forEach { out += it.lowercase() }
        }
        return out
    }

    private fun taskStates(markdown: String): List<Boolean> =
        Regex("""(?m)^\s*[-*+]\s+\[([ xX])]""").findAll(markdown).map { it.groupValues[1] != " " }.toList()

    private fun multisetMinus(a: List<String>, b: List<String>): List<String> {
        val counts = b.groupingBy { it }.eachCount().toMutableMap()
        return a.filter { w ->
            val c = counts[w] ?: 0
            if (c > 0) {
                counts[w] = c - 1
                false
            } else {
                true
            }
        }.distinct()
    }

    private const val MAX_LISTED = 6
}
