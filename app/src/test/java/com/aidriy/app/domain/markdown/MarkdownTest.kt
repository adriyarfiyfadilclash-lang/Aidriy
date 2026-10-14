package com.aidriy.app.domain.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    @Test
    fun parses_headings_paragraphs_and_keeps_line_breaks() {
        val blocks = Markdown.parse("# Judul\nBaris satu\nbaris dua\n\n### Sub")
        assertEquals(MdBlock.Heading(1, "Judul", 0), blocks[0])
        assertEquals(MdBlock.Paragraph("Baris satu\nbaris dua"), blocks[1])
        assertEquals(MdBlock.Heading(3, "Sub", 4), blocks[2])
    }

    @Test
    fun parses_lists_tasks_and_nesting() {
        val list = Markdown.parse("- satu\n  - anak\n- [ ] tugas\n- [x] selesai\n3. tiga\n4) empat").single() as MdBlock.ListBlock
        val items = list.items
        assertEquals(ListKind.BULLET, items[0].kind)
        assertEquals(1, items[1].depth)
        assertEquals(ListKind.TASK, items[2].kind)
        assertEquals(false, items[2].checked)
        assertEquals(true, items[3].checked)
        assertEquals(ListKind.ORDERED, items[4].kind)
        assertEquals(3, items[4].number)
        assertEquals(4, items[5].number)
        assertEquals(2, items[2].line)
    }

    @Test
    fun parses_quote_callout_code_divider_table_and_image() {
        val md = """
            > kutipan
            > lanjut

            > [!PENTING] Perhatian
            > isi

            ```kotlin
            val x = 1
            ```
            ---
            | A | B |
            |:--|--:|
            | 1 | 2 \| 3 |

            ![Gambar 1](berkas://abc "sedang")
        """.trimIndent()
        val blocks = Markdown.parse(md)
        assertEquals(MdBlock.Quote("kutipan\nlanjut"), blocks[0])
        assertEquals(MdBlock.Callout(CalloutKind.IMPORTANT, "Perhatian", "isi"), blocks[1])
        assertEquals(MdBlock.Code("kotlin", "val x = 1"), blocks[2])
        assertEquals(MdBlock.Divider, blocks[3])
        val table = blocks[4] as MdBlock.Table
        assertEquals(listOf("A", "B"), table.header)
        assertEquals(listOf(ColumnAlign.START, ColumnAlign.END), table.align)
        assertEquals(listOf(listOf("1", "2 | 3")), table.rows)
        assertEquals(MdBlock.Image("berkas://abc", "Gambar 1", ImageSize.MEDIUM), blocks[5])
    }

    @Test
    fun toggles_only_task_lines() {
        val text = "- [ ] a\n- b\n  - [x] c"
        assertEquals("- [x] a\n- b\n  - [x] c", Markdown.toggleTask(text, 0))
        assertEquals("- [ ] a\n- b\n  - [ ] c", Markdown.toggleTask(text, 2))
        assertEquals(text, Markdown.toggleTask(text, 1))
        assertEquals(text, Markdown.toggleTask(text, 9))
    }

    @Test
    fun image_line_round_trips() {
        val line = Markdown.imageLine("berkas://id1", "Area [oven]", ImageSize.SMALL)
        assertEquals("![Area  oven](berkas://id1 \"kecil\")", line)
        val block = Markdown.parse(line).single() as MdBlock.Image
        assertEquals(ImageSize.SMALL, block.size)
        assertEquals("id1", Markdown.berkasId(block.target))
        assertEquals("![x](berkas://id1)", Markdown.imageLine("berkas://id1", "x", ImageSize.FULL))
    }

    @Test
    fun inline_styles_links_and_escapes() {
        val runs = MarkdownInline.parse("a **b _c_** ~~d~~ ==e== `f` [g](https://g.id) \\*h\\* https://x.id/p.")
        val byText = runs.associateBy { it.text }
        assertTrue(byText.getValue("b ").bold)
        assertTrue(byText.getValue("c").bold && byText.getValue("c").italic)
        assertTrue(byText.getValue("d").strike)
        assertTrue(byText.getValue("e").highlight)
        assertTrue(byText.getValue("f").code)
        assertEquals("https://g.id", byText.getValue("g").link)
        assertEquals("https://x.id/p", byText.getValue("https://x.id/p").link)
        assertEquals("a b c d e f g *h* https://x.id/p.", MarkdownInline.plain("a **b _c_** ~~d~~ ==e== `f` [g](https://g.id) \\*h\\* https://x.id/p."))
    }

    @Test
    fun underscores_inside_words_are_not_italic() {
        assertEquals(listOf(InlineRun("nama_file_baru")), MarkdownInline.parse("nama_file_baru"))
    }
}
