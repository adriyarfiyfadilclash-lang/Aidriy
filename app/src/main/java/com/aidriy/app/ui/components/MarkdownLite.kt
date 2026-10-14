package com.aidriy.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * Notes are stored as plain text with a small Markdown subset, so they stay readable in any app
 * and in backups: **tebal**, *miring*, __garis bawah__, # judul, - poin, 1. nomor, - [ ] tugas,
 * [teks](https://tautan).
 */
object MarkdownLite {

    private val bold = Regex("""\*\*(.+?)\*\*""")
    private val underline = Regex("""__(.+?)__""")
    private val italic = Regex("""(?<![*\w])\*(?!\s|\*)(.+?)(?<!\s|\*)\*(?![*\w])""")
    private val link = Regex("""\[([^\]\n]+)]\(([^)\s]+)\)""")
    private val heading = Regex("""(?m)^(#{1,3}) """)
    private val listMarker = Regex("""(?m)^(\s*)(- \[[ xX]] |- |\d+\. )""")

    /** Plain preview text for lists and search results (markers removed, whitespace collapsed). */
    fun plain(text: String, max: Int = 300): String {
        if (text.isBlank()) return ""
        var s = text.take(max * 3)
        s = link.replace(s) { it.groupValues[1] }
        s = bold.replace(s) { it.groupValues[1] }
        s = underline.replace(s) { it.groupValues[1] }
        s = italic.replace(s) { it.groupValues[1] }
        s = s.replace(Regex("""(?m)^#{1,3} """), "")
        s = s.replace(Regex("""(?m)^\s*- \[[xX]] """), "☑ ")
        s = s.replace(Regex("""(?m)^\s*- \[ ] """), "☐ ")
        s = s.replace(Regex("""(?m)^\s*- """), "• ")
        return s.replace(Regex("""\s+"""), " ").trim().take(max)
    }

    /** Styles markers in place (text and cursor positions are unchanged). */
    fun styled(text: String, markerColor: Color, linkColor: Color): AnnotatedString = buildAnnotatedString {
        append(text)
        if (text.length > MAX_STYLED) return@buildAnnotatedString
        val marker = SpanStyle(color = markerColor)
        fun mark(range: IntRange, markerLen: Int, style: SpanStyle) {
            addStyle(style, range.first + markerLen, range.last + 1 - markerLen)
            addStyle(marker, range.first, range.first + markerLen)
            addStyle(marker, range.last + 1 - markerLen, range.last + 1)
        }
        bold.findAll(text).forEach { mark(it.range, 2, SpanStyle(fontWeight = FontWeight.Bold)) }
        underline.findAll(text).forEach { mark(it.range, 2, SpanStyle(textDecoration = TextDecoration.Underline)) }
        italic.findAll(text).forEach { mark(it.range, 1, SpanStyle(fontStyle = FontStyle.Italic)) }
        link.findAll(text).forEach { m ->
            val label = m.groups[1]!!.range
            addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), label.first, label.last + 1)
            addStyle(marker, m.range.first, label.first)
            addStyle(marker, label.last + 1, m.range.last + 1)
        }
        heading.findAll(text).forEach { m ->
            val lineEnd = text.indexOf('\n', m.range.last).let { if (it < 0) text.length else it }
            val size = when (m.groupValues[1].length) { 1 -> 22.sp; 2 -> 19.sp; else -> 17.sp }
            addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = size), m.range.last + 1, lineEnd)
            addStyle(marker, m.range.first, m.range.last + 1)
        }
        listMarker.findAll(text).forEach { m ->
            val g = m.groups[2]!!.range
            addStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Bold), g.first, g.last + 1)
        }
    }

    /**
     * Read-only rendering (AI answers): markers removed, styles applied, bullets shown as "•".
     */
    fun render(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
        val inline = Regex("""\*\*(.+?)\*\*|__(.+?)__|(?<![*\w])\*(?!\s|\*)(.+?)(?<!\s|\*)\*(?![*\w])|`([^`\n]+)`|\[([^\]\n]+)]\(([^)\s]+)\)""")
        text.lines().forEachIndexed { index, rawLine ->
            if (index > 0) append('\n')
            var line = rawLine
            var lineStyle: SpanStyle? = null
            val h = Regex("""^(#{1,6}) (.*)$""").find(line)
            if (h != null) {
                line = h.groupValues[2]
                lineStyle = SpanStyle(fontWeight = FontWeight.Bold, fontSize = if (h.groupValues[1].length <= 2) 18.sp else 16.sp)
            } else {
                val bullet = Regex("""^(\s*)[-*] (?!\[)(.*)$""").find(line)
                val check = Regex("""^(\s*)- \[([ xX])] (.*)$""").find(line)
                when {
                    check != null -> line = check.groupValues[1] + (if (check.groupValues[2] == " ") "☐ " else "☑ ") + check.groupValues[3]
                    bullet != null -> line = bullet.groupValues[1] + "• " + bullet.groupValues[2]
                }
            }
            val start = length
            var cursor = 0
            inline.findAll(line).forEach { m ->
                append(line.substring(cursor, m.range.first))
                val g = m.groups
                when {
                    g[1] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[1]!!.value) }
                    g[2] != null -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(g[2]!!.value) }
                    g[3] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[3]!!.value) }
                    g[4] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(g[4]!!.value) }
                    g[5] != null -> withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) { append(g[5]!!.value) }
                }
                cursor = m.range.last + 1
            }
            append(line.substring(cursor))
            lineStyle?.let { addStyle(it, start, length) }
        }
    }

    fun visualTransformation(markerColor: Color, linkColor: Color) = VisualTransformation { text ->
        TransformedText(styled(text.text, markerColor, linkColor), OffsetMapping.Identity)
    }

    // ---------------------------------------------------------------- editing helpers

    /** Wraps the selection in [marker] (or inserts an empty pair with the cursor inside). */
    fun toggleWrap(value: TextFieldValue, marker: String): TextFieldValue {
        val text = value.text
        val start = value.selection.min
        val end = value.selection.max
        val selected = text.substring(start, end)
        // Already wrapped: unwrap.
        if (start >= marker.length && end + marker.length <= text.length &&
            text.substring(start - marker.length, start) == marker && text.substring(end, end + marker.length) == marker
        ) {
            val newText = text.removeRange(end, end + marker.length).removeRange(start - marker.length, start)
            return TextFieldValue(newText, TextRange(start - marker.length, end - marker.length))
        }
        val newText = text.replaceRange(start, end, "$marker$selected$marker")
        return if (selected.isEmpty()) {
            TextFieldValue(newText, TextRange(start + marker.length))
        } else {
            TextFieldValue(newText, TextRange(start + marker.length, end + marker.length))
        }
    }

    /** Adds [prefix] to every selected line; for numbered lists pass "1. " and lines are numbered. */
    fun prefixLines(value: TextFieldValue, prefix: String, numbered: Boolean = false): TextFieldValue {
        val text = value.text
        val lineStart = text.lastIndexOf('\n', (value.selection.min - 1).coerceAtLeast(-1)).let { it + 1 }
            .coerceAtMost(value.selection.min)
        val lineEnd = text.indexOf('\n', value.selection.max).let { if (it < 0) text.length else it }
        val block = text.substring(lineStart, lineEnd)
        var n = 0
        val newBlock = block.split('\n').joinToString("\n") { line ->
            n++
            val clean = line.replace(Regex("""^(- \[[ xX]] |- |\d+\. |#{1,3} )"""), "")
            if (numbered) "$n. $clean" else "$prefix$clean"
        }
        val newText = text.replaceRange(lineStart, lineEnd, newBlock)
        return TextFieldValue(newText, TextRange(lineStart + newBlock.length))
    }

    fun insert(value: TextFieldValue, insertion: String): TextFieldValue {
        val start = value.selection.min
        val newText = value.text.replaceRange(start, value.selection.max, insertion)
        return TextFieldValue(newText, TextRange(start + insertion.length))
    }

    /** Inserts text on its own line at the cursor (used for dictation and links). */
    fun insertBlock(value: TextFieldValue, block: String): TextFieldValue {
        val text = value.text
        val at = value.selection.max
        val before = if (at > 0 && text[at - 1] != '\n') "\n" else ""
        return insert(TextFieldValue(text, TextRange(at)), before + block)
    }

    private const val MAX_STYLED = 60_000
}
