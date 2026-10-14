package com.aidriy.app.domain.markdown

/*
 * Markdown used by AIDRIY notes. Plain text stays the storage format (readable in backups and
 * other apps); this file turns it into blocks and inline runs for the reader.
 *
 * Supported:
 *   # .. ###### headings, paragraphs (single line breaks kept), - * + bullets, 1. / 1) numbers,
 *   - [ ] / - [x] tasks, nesting by indentation, > quotes (nested, with lists/code inside),
 *   > [!INFO] callouts (foldable with [!TIP]- / [!TIP]+), ``` code blocks (highlighted),
 *   --- dividers, | tables |, ![keterangan](berkas://id "ukuran") images, [TOC] outline,
 *   **tebal**, *miring* / _miring_, __garis bawah__, ~~coret~~, ==sorot==, `kode`,
 *   [tautan](https://...), [[Judul catatan]] / [[Judul|teks]] links, bare https:// links, \ escapes.
 */

enum class ListKind { BULLET, ORDERED, TASK }

enum class CalloutKind { INFO, TIP, IMPORTANT, WARNING, DANGER, SUCCESS, QUESTION, EXAMPLE }

enum class ColumnAlign { START, CENTER, END }

/** Image width in the note. Stored as the image title: "kecil", "sedang" or "penuh". */
enum class ImageSize(val key: String, val fraction: Float) {
    SMALL("kecil", 0.45f),
    MEDIUM("sedang", 0.7f),
    FULL("penuh", 1f);

    fun next(): ImageSize = entries[(ordinal + 1) % entries.size]

    companion object {
        fun from(key: String?): ImageSize = entries.firstOrNull { it.key == key?.trim()?.lowercase() } ?: FULL
    }
}

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String, val line: Int) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class ListItem(
        val kind: ListKind,
        val text: String,
        val depth: Int,
        /** Number as written for ordered items. */
        val number: Int = 0,
        val checked: Boolean = false,
        /** Source line, used to toggle tasks from the reader. */
        val line: Int,
    )
    data class ListBlock(val items: List<ListItem>) : MdBlock
    /** Quote body is Markdown itself, so quotes can nest and hold lists or code. */
    data class Quote(val text: String) : MdBlock
    data class Callout(
        val kind: CalloutKind,
        val title: String,
        val text: String,
        /** `[!TIP]-` (starts closed) or `[!TIP]+` (starts open): the reader can fold it. */
        val foldable: Boolean = false,
        val collapsed: Boolean = false,
    ) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
    data object Divider : MdBlock
    data class Table(val header: List<String>, val align: List<ColumnAlign>, val rows: List<List<String>>) : MdBlock
    data class Image(val target: String, val caption: String, val size: ImageSize) : MdBlock

    /** `[TOC]` line: outline of the note's headings. */
    data class Toc(val headings: List<Heading>) : MdBlock
}

object Markdown {

    const val BERKAS_SCHEME = "berkas://"

    /** Link target used for [[Judul catatan]] links. */
    const val NOTE_SCHEME = "catatan:"

    private val headingRe = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
    private val listRe = Regex("""^(\s*)([-*+]|\d{1,9}[.)])\s+(.*)$""")
    private val taskRe = Regex("""^\[([ xX])]\s+(.*)$|^\[([ xX])]$""")
    private val fenceRe = Regex("""^\s*(```|~~~)\s*([\w+-]*)\s*$""")
    private val dividerRe = Regex("""^\s{0,3}((\*\s*){3,}|(-\s*){3,}|(_\s*){3,})$""")
    private val calloutRe = Regex("""^\[!(\w+)]([+-]?)\s*(.*)$""")
    private val tocRe = Regex("""^\s*\[{1,2}(TOC|DAFTAR ISI)]{1,2}\s*$""", RegexOption.IGNORE_CASE)
    private val tableSepRe = Regex("""^\s*\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)*\|?\s*$""")
    val imageLineRe = Regex("""^\s*!\[([^\]]*)]\(\s*(\S+?)(?:\s+"([^"]*)")?\s*\)\s*$""")

    fun parse(text: String): List<MdBlock> {
        val lines = text.replace("\r\n", "\n").split('\n')
        val blocks = mutableListOf<MdBlock>()
        var i = 0
        val paragraph = mutableListOf<String>()
        val listItems = mutableListOf<MdBlock.ListItem>()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += MdBlock.Paragraph(paragraph.joinToString("\n") { it.trimEnd() })
                paragraph.clear()
            }
        }
        fun flushList() {
            if (listItems.isNotEmpty()) {
                blocks += MdBlock.ListBlock(listItems.toList())
                listItems.clear()
            }
        }
        fun flushAll() {
            flushParagraph()
            flushList()
        }

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                flushAll()
                i++
                continue
            }

            val fence = fenceRe.find(line)
            if (fence != null) {
                flushAll()
                val marker = fence.groupValues[1]
                val lang = fence.groupValues[2]
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && lines[i].trim() != marker) {
                    code += lines[i]
                    i++
                }
                i++ // closing fence (or end of text)
                blocks += MdBlock.Code(lang, code.joinToString("\n"))
                continue
            }

            val heading = headingRe.find(line)
            if (heading != null) {
                flushAll()
                blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2], i)
                i++
                continue
            }

            if (tocRe.matches(line)) {
                flushAll()
                blocks += MdBlock.Toc(emptyList())
                i++
                continue
            }

            if (dividerRe.matches(line)) {
                flushAll()
                blocks += MdBlock.Divider
                i++
                continue
            }

            val image = imageLineRe.find(line)
            if (image != null) {
                flushAll()
                blocks += MdBlock.Image(image.groupValues[2], image.groupValues[1], ImageSize.from(image.groupValues[3]))
                i++
                continue
            }

            if (trimmed.startsWith(">")) {
                flushAll()
                val quoted = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quoted += lines[i].trim().removePrefix(">").removePrefix(" ")
                    i++
                }
                val first = calloutRe.find(quoted.first())
                blocks += if (first != null) {
                    MdBlock.Callout(
                        kind = calloutKind(first.groupValues[1]),
                        title = first.groupValues[3],
                        text = quoted.drop(1).joinToString("\n"),
                        foldable = first.groupValues[2].isNotEmpty(),
                        collapsed = first.groupValues[2] == "-",
                    )
                } else {
                    MdBlock.Quote(quoted.joinToString("\n"))
                }
                continue
            }

            if (trimmed.startsWith("|") && i + 1 < lines.size && tableSepRe.matches(lines[i + 1])) {
                flushAll()
                val header = cells(line)
                val align = cells(lines[i + 1]).map { c ->
                    val t = c.trim()
                    when {
                        t.startsWith(":") && t.endsWith(":") -> ColumnAlign.CENTER
                        t.endsWith(":") -> ColumnAlign.END
                        else -> ColumnAlign.START
                    }
                }
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    rows += cells(lines[i])
                    i++
                }
                val width = header.size
                blocks += MdBlock.Table(
                    header = header,
                    align = List(width) { align.getOrElse(it) { ColumnAlign.START } },
                    rows = rows.map { r -> List(width) { r.getOrElse(it) { "" } } },
                )
                continue
            }

            val lm = listRe.find(line)
            if (lm != null) {
                flushParagraph()
                val indent = lm.groupValues[1].replace("\t", "    ").length
                val marker = lm.groupValues[2]
                val rest = lm.groupValues[3]
                val task = if (marker.length == 1) taskRe.find(rest) else null
                listItems += when {
                    task != null -> MdBlock.ListItem(
                        kind = ListKind.TASK,
                        text = task.groupValues[2],
                        depth = indent / 2,
                        checked = (task.groupValues[1].ifEmpty { task.groupValues[3] }).lowercase() == "x",
                        line = i,
                    )
                    marker.length == 1 -> MdBlock.ListItem(ListKind.BULLET, rest, indent / 2, line = i)
                    else -> MdBlock.ListItem(
                        ListKind.ORDERED,
                        rest,
                        indent / 2,
                        number = marker.dropLast(1).toIntOrNull() ?: 1,
                        line = i,
                    )
                }
                i++
                continue
            }

            // Indented continuation of a list item.
            if (listItems.isNotEmpty() && line.startsWith("  ")) {
                val last = listItems.removeAt(listItems.lastIndex)
                listItems += last.copy(text = last.text + "\n" + trimmed)
                i++
                continue
            }

            flushList()
            paragraph += line
            i++
        }
        flushAll()
        if (blocks.none { it is MdBlock.Toc }) return blocks
        val headings = blocks.filterIsInstance<MdBlock.Heading>()
        return blocks.map { if (it is MdBlock.Toc) MdBlock.Toc(headings) else it }
    }

    private fun cells(line: String): List<String> {
        var t = line.trim()
        if (t.startsWith("|")) t = t.drop(1)
        if (t.endsWith("|") && !t.endsWith("\\|")) t = t.dropLast(1)
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var k = 0
        while (k < t.length) {
            val c = t[k]
            if (c == '\\' && k + 1 < t.length && t[k + 1] == '|') {
                cur.append('|')
                k += 2
                continue
            }
            if (c == '|') {
                out += cur.toString().trim()
                cur.clear()
            } else {
                cur.append(c)
            }
            k++
        }
        out += cur.toString().trim()
        return out
    }

    fun calloutKind(raw: String): CalloutKind = when (raw.uppercase()) {
        "TIP", "SARAN", "HINT" -> CalloutKind.TIP
        "PENTING", "IMPORTANT" -> CalloutKind.IMPORTANT
        "PERINGATAN", "WARNING", "CAUTION", "AWAS" -> CalloutKind.WARNING
        "BAHAYA", "DANGER", "ERROR", "GAGAL", "BUG" -> CalloutKind.DANGER
        "SUKSES", "SUCCESS", "BERHASIL", "SELESAI", "DONE", "CHECK" -> CalloutKind.SUCCESS
        "PERTANYAAN", "QUESTION", "FAQ", "TANYA" -> CalloutKind.QUESTION
        "CONTOH", "EXAMPLE" -> CalloutKind.EXAMPLE
        else -> CalloutKind.INFO
    }

    /**
     * One-line plain text for lists, cards and search results: Markdown markers removed, tasks
     * shown as ☐/☑, bullets as •, images as 🖼, code fences and table rules dropped.
     */
    fun preview(text: String, max: Int = 300): String {
        if (text.isBlank()) return ""
        val out = StringBuilder()
        for (raw in text.take(max * 4).split('\n')) {
            var line = raw.trim()
            if (fenceRe.matches(line) || tableSepRe.matches(line) || dividerRe.matches(line) || tocRe.matches(line)) continue
            imageLineRe.find(line)?.let { line = "🖼 " + it.groupValues[1] }
            while (line.startsWith(">")) line = line.removePrefix(">").trimStart()
            calloutRe.find(line)?.let { line = it.groupValues[3] }
            line = line.replace(Regex("""^#{1,6}\s+"""), "")
            line = line.replace(Regex("""^[-*+]\s+\[[xX]]\s*"""), "☑ ")
                .replace(Regex("""^[-*+]\s+\[ ]\s*"""), "☐ ")
                .replace(Regex("""^[-*+]\s+"""), "• ")
            if (line.startsWith("|")) line = cells(line).joinToString(" · ")
            line = MarkdownInline.plain(line)
            if (line.isNotBlank()) out.append(line).append(' ')
            if (out.length > max) break
        }
        return out.toString().replace(Regex("""\s+"""), " ").trim().take(max)
    }

    /** Keyword written for a callout picked in the editor (Bahasa Indonesia where it reads well). */
    fun calloutKeyword(kind: CalloutKind): String = when (kind) {
        CalloutKind.INFO -> "INFO"
        CalloutKind.TIP -> "TIP"
        CalloutKind.IMPORTANT -> "PENTING"
        CalloutKind.WARNING -> "PERINGATAN"
        CalloutKind.DANGER -> "BAHAYA"
        CalloutKind.SUCCESS -> "SUKSES"
        CalloutKind.QUESTION -> "PERTANYAAN"
        CalloutKind.EXAMPLE -> "CONTOH"
    }

    /** Flips "- [ ]" / "- [x]" on [line]. Returns the text unchanged if that line is not a task. */
    fun toggleTask(text: String, line: Int): String {
        val lines = text.split('\n').toMutableList()
        if (line !in lines.indices) return text
        val re = Regex("""^(\s*[-*+]\s+\[)([ xX])(].*)$""")
        val m = re.find(lines[line]) ?: return text
        val flipped = if (m.groupValues[2] == " ") "x" else " "
        lines[line] = m.groupValues[1] + flipped + m.groupValues[3]
        return lines.joinToString("\n")
    }

    /** Markdown for an image block. */
    fun imageLine(target: String, caption: String, size: ImageSize): String {
        val cleanCaption = caption.replace(Regex("[\\[\\]\\n]"), " ").trim()
        return if (size == ImageSize.FULL) "![$cleanCaption]($target)" else "![$cleanCaption]($target \"${size.key}\")"
    }

    fun berkasTarget(fileId: String) = BERKAS_SCHEME + fileId

    fun berkasId(target: String): String? =
        if (target.startsWith(BERKAS_SCHEME)) target.removePrefix(BERKAS_SCHEME).takeIf { it.isNotBlank() } else null

    /** Title from a [[Judul]] link target, or null for other links. */
    fun noteTitle(target: String): String? =
        if (target.startsWith(NOTE_SCHEME)) target.removePrefix(NOTE_SCHEME).trim().takeIf { it.isNotEmpty() } else null
}

/** One styled piece of inline text. */
data class InlineRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val code: Boolean = false,
    val highlight: Boolean = false,
    val link: String? = null,
)

object MarkdownInline {

    private data class Style(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val highlight: Boolean = false,
        val link: String? = null,
    )

    private val pattern = Regex(
        listOf(
            """\\([\\`*_~=\[\]()#>|!-])""",                 // 1 escape
            """`([^`\n]+)`""",                                // 2 code
            """\[\[([^\]|\n]+)(?:\|([^\]\n]+))?]]""",        // 3,4 note link [[Judul|teks]]
            """\[([^\]\n]+)]\(([^)\s]+)\)""",                 // 5,6 link
            """\*\*(.+?)\*\*""",                              // 7 bold
            """__(.+?)__""",                                  // 8 underline (AIDRIY convention)
            """~~(.+?)~~""",                                  // 9 strike
            """==(.+?)==""",                                  // 10 highlight
            """(?<![*\w])\*(?![\s*])(.+?)(?<![\s*])\*(?![*\w])""", // 11 italic *
            """(?<![_\w])_(?![\s_])(.+?)(?<![\s_])_(?![_\w])""",   // 12 italic _
            """(https?://[^\s<>()\[\]]+[^\s<>()\[\].,;:!?'"])""",    // 13 bare url
        ).joinToString("|"),
    )

    fun parse(text: String): List<InlineRun> {
        val out = mutableListOf<InlineRun>()
        parseInto(text, Style(), out)
        // Merge neighbours with identical style.
        val merged = mutableListOf<InlineRun>()
        for (r in out) {
            val last = merged.lastOrNull()
            if (last != null && last.copy(text = "") == r.copy(text = "")) {
                merged[merged.lastIndex] = last.copy(text = last.text + r.text)
            } else if (r.text.isNotEmpty()) {
                merged += r
            }
        }
        return merged
    }

    private fun run(text: String, s: Style, code: Boolean = false) = InlineRun(
        text = text, bold = s.bold, italic = s.italic, underline = s.underline, strike = s.strike,
        code = code, highlight = s.highlight, link = s.link,
    )

    private fun parseInto(text: String, style: Style, out: MutableList<InlineRun>) {
        var cursor = 0
        for (m in pattern.findAll(text)) {
            if (m.range.first < cursor) continue
            out += run(text.substring(cursor, m.range.first), style)
            val g = m.groups
            when {
                g[1] != null -> out += run(g[1]!!.value, style)
                g[2] != null -> out += run(g[2]!!.value, style, code = true)
                g[3] != null -> {
                    val title = g[3]!!.value.trim()
                    out += run((g[4]?.value ?: title).trim(), style.copy(link = Markdown.NOTE_SCHEME + title))
                }
                g[5] != null -> parseInto(g[5]!!.value, style.copy(link = g[6]!!.value), out)
                g[7] != null -> parseInto(g[7]!!.value, style.copy(bold = true), out)
                g[8] != null -> parseInto(g[8]!!.value, style.copy(underline = true), out)
                g[9] != null -> parseInto(g[9]!!.value, style.copy(strike = true), out)
                g[10] != null -> parseInto(g[10]!!.value, style.copy(highlight = true), out)
                g[11] != null -> parseInto(g[11]!!.value, style.copy(italic = true), out)
                g[12] != null -> parseInto(g[12]!!.value, style.copy(italic = true), out)
                g[13] != null -> out += run(g[13]!!.value, style.copy(link = style.link ?: g[13]!!.value))
            }
            cursor = m.range.last + 1
        }
        out += run(text.substring(cursor), style)
    }

    /** Text without markers (previews, comparisons). */
    fun plain(text: String): String = parse(text).joinToString("") { it.text }
}
