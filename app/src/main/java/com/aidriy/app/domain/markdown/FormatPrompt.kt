package com.aidriy.app.domain.markdown

/** What "Rapikan Format" may change. Images and Berkas links are always kept. */
data class FormatOptions(
    val spacing: Boolean = true,
    val headingsAndLists: Boolean = true,
    val indentation: Boolean = true,
    val emphasis: Boolean = false,
    val richBlocks: Boolean = false,
)

/** Instructions for the AI. Kept here (pure Kotlin) so they can be unit-tested. */
object FormatPrompt {

    /** Longer notes are refused up front: the answer would likely be cut off. */
    const val MAX_CHARS = 24_000

    fun system(options: FormatOptions): String = buildString {
        appendLine("You reformat a personal note written in Markdown. The note is usually in Indonesian.")
        appendLine("Return ONLY the reformatted Markdown. No explanations, no greetings, no code fence around the answer.")
        appendLine()
        appendLine("Absolute rules (the app verifies them and rejects your answer otherwise):")
        appendLine("- Keep every word of the note, in the same order. Do not add, remove, translate, summarise, correct or reword anything.")
        appendLine("- Do not add new headings, titles, labels or table headers that are not already words in the note.")
        appendLine("- You may change capitalisation, punctuation, whitespace, line breaks and Markdown markers only.")
        appendLine("- Keep placeholders like ⟦GAMBAR_1⟧ and ⟦BERKAS_1⟧ exactly as they are, each on the same position relative to the text. Images (⟦GAMBAR_n⟧) stay on their own line.")
        appendLine("- Keep checklist states exactly ([ ] stays [ ], [x] stays [x]).")
        appendLine("- Keep code blocks (```) unchanged, including their content.")
        appendLine("- Use this Markdown: # ## ### headings, - bullets, 1. numbers, - [ ] tasks, > quotes, **bold**, *italic*, __underline__, ~~strike~~, ==highlight==, `code`, | tables |, --- dividers, > [!INFO] / [!TIP] / [!PENTING] / [!PERINGATAN] callouts.")
        appendLine()
        appendLine("Allowed improvements:")
        if (options.spacing) appendLine("- Tidy spacing: one blank line between paragraphs and blocks, no trailing spaces, no runs of blank lines.")
        if (options.headingsAndLists) {
            appendLine("- Turn lines that act as section titles (for example '1. pengertian coking plant' alone on a line before a paragraph) into Markdown headings, keeping their words and numbers.")
            appendLine("- Turn sequences of items into proper bullet, numbered or checklist lists. Capitalise the first letter of headings, list items and sentences.")
        }
        if (options.indentation) appendLine("- Fix indentation of nested lists (2 spaces per level) and remove stray leading spaces from paragraphs.")
        if (options.emphasis) appendLine("- Mark a few key terms in **bold** (only existing words).")
        if (options.richBlocks) {
            appendLine("- Where it clearly fits, use a table (header cells must be existing words from the note), a > quote, a --- divider, or a callout written as '> [!INFO]' with no title text followed by '> ' lines.")
        }
        if (!options.spacing && !options.headingsAndLists && !options.indentation && !options.emphasis && !options.richBlocks) {
            appendLine("- Only fix obviously broken Markdown syntax.")
        }
    }.trim()

    fun user(title: String, protectedMarkdown: String): String = buildString {
        if (title.isNotBlank()) {
            appendLine("Note title (context only, do not output it): $title")
            appendLine()
        }
        appendLine("Note to reformat:")
        appendLine("<<<")
        appendLine(protectedMarkdown)
        append(">>>")
    }

    /** Room for the reformatted note: roughly one token per 3 characters, plus margin. */
    fun maxOutputTokens(chars: Int): Int = (chars / 3 + 1_024).coerceIn(2_048, 16_000)
}
