package com.aidriy.app.domain.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatGuardTest {

    private val original = """
        1. pengertian coking plant
        Coking plant adalah fasilitas.
        ![Gambar 1](berkas://img1 "sedang")
        2. proses utama
        batubara dimasukkan. Lihat [laporan](berkas://f2)
        - [ ] cek suhu
    """.trimIndent()

    private val tidy = """
        ```markdown
        ## 1. Pengertian Coking Plant

        Coking plant adalah **fasilitas**.

        ⟦GAMBAR_1⟧

        ## 2. Proses Utama

        Batubara dimasukkan. Lihat ⟦BERKAS_1⟧

        - [ ] Cek suhu
        ```
    """.trimIndent()

    @Test
    fun protect_replaces_images_and_berkas_links_with_placeholders() {
        val p = FormatGuard.protect(original)
        assertTrue("berkas://" !in p.text)
        assertEquals(setOf("⟦GAMBAR_1⟧", "⟦BERKAS_1⟧"), p.tokens.keys)
    }

    @Test
    fun formatting_only_change_is_accepted_and_restored() {
        val p = FormatGuard.protect(original)
        val result = FormatGuard.check(original, p, tidy)
        assertTrue(result is FormatGuard.Result.Ok)
        val text = (result as FormatGuard.Result.Ok).text
        assertTrue(text.contains("![Gambar 1](berkas://img1 \"sedang\")"))
        assertTrue(text.contains("[laporan](berkas://f2)"))
        assertTrue(!text.startsWith("```"))
    }

    @Test
    fun added_words_are_reported() {
        val p = FormatGuard.protect(original)
        val result = FormatGuard.check(original, p, tidy.replace("dimasukkan.", "dimasukkan ke oven."))
        assertTrue(result is FormatGuard.Result.TextChanged)
        assertEquals(listOf("ke", "oven"), (result as FormatGuard.Result.TextChanged).added)
    }

    @Test
    fun removed_words_are_reported() {
        val p = FormatGuard.protect(original)
        val result = FormatGuard.check(original, p, tidy.replace("Coking plant adalah **fasilitas**.", "Coking plant."))
        assertEquals(listOf("adalah", "fasilitas"), (result as FormatGuard.Result.TextChanged).removed)
    }

    @Test
    fun dropped_image_is_refused() {
        val p = FormatGuard.protect(original)
        assertTrue(FormatGuard.check(original, p, tidy.replace("⟦GAMBAR_1⟧", "")) is FormatGuard.Result.LostBlocks)
        assertTrue(FormatGuard.check(original, p, tidy + "\n⟦GAMBAR_1⟧") is FormatGuard.Result.LostBlocks)
    }

    @Test
    fun flipped_checkbox_counts_as_a_change() {
        val p = FormatGuard.protect(original)
        val result = FormatGuard.check(original, p, tidy.replace("- [ ] Cek", "- [x] Cek"))
        assertTrue(result is FormatGuard.Result.TextChanged)
    }

    @Test
    fun words_ignore_markers_case_and_punctuation() {
        assertEquals(
            FormatGuard.words("1. halo dunia\n> [!INFO]\n> catatan"),
            FormatGuard.words("## 1. Halo, Dunia!\n\n- **Catatan**"),
        )
    }

    @Test
    fun prompt_lists_only_enabled_options() {
        val all = FormatPrompt.system(FormatOptions(emphasis = true, richBlocks = true))
        val minimal = FormatPrompt.system(FormatOptions(spacing = false, headingsAndLists = false, indentation = false))
        assertTrue(all.contains("table"))
        assertTrue(!minimal.contains("Tidy spacing"))
        assertTrue(minimal.contains("Only fix obviously broken"))
        assertTrue(FormatPrompt.user("Judul", "isi").contains("<<<\nisi\n>>>"))
        assertEquals(2_048, FormatPrompt.maxOutputTokens(100))
        assertEquals(16_000, FormatPrompt.maxOutputTokens(FormatPrompt.MAX_CHARS * 3))
    }
}
