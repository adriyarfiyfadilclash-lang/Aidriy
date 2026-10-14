package com.aidriy.app.ui.notes.templates

import com.aidriy.app.core.time.IndonesianDateFormatter
import com.aidriy.app.ui.theme.AccentKey
import java.time.LocalDate

enum class TemplateCategory(val label: String) { BELAJAR("Belajar"), KERJA("Kerja"), PRIBADI("Pribadi") }

/**
 * Built-in note structures. They only provide headings and empty list items; nothing in them
 * pretends to be the user's own content.
 */
data class NoteTemplate(
    val id: String,
    val title: String,
    val description: String,
    val category: TemplateCategory,
    val accent: AccentKey,
    private val body: String,
) {
    fun render(today: LocalDate): String = body.replace("{tanggal}", IndonesianDateFormatter.formatLong(today))
}

object NoteTemplates {
    val all: List<NoteTemplate> = listOf(
        NoteTemplate(
            id = "catatan_cepat",
            title = "Catatan Cepat",
            description = "Template minimalis untuk ide cepat",
            category = TemplateCategory.PRIBADI,
            accent = AccentKey.YELLOW,
            body = "- \n",
        ),
        NoteTemplate(
            id = "todo",
            title = "To Do List",
            description = "Daftar tugas harian",
            category = TemplateCategory.KERJA,
            accent = AccentKey.PURPLE,
            body = "# Tugas {tanggal}\n\n- [ ] \n- [ ] \n- [ ] \n",
        ),
        NoteTemplate(
            id = "rangkuman",
            title = "Rangkuman Materi",
            description = "Format ringkasan pelajaran",
            category = TemplateCategory.BELAJAR,
            accent = AccentKey.BLUE,
            body = "# Topik\n\n## Poin utama\n- \n- \n\n## Istilah penting\n- \n\n## Contoh\n\n## Pertanyaan yang belum terjawab\n- \n",
        ),
        NoteTemplate(
            id = "meeting",
            title = "Meeting Notes",
            description = "Catatan rapat atau diskusi",
            category = TemplateCategory.KERJA,
            accent = AccentKey.BLUE,
            body = "# Rapat\n**Tanggal:** {tanggal}\n**Peserta:** \n\n## Agenda\n1. \n\n## Pembahasan\n\n## Keputusan\n- \n\n## Tindak lanjut\n- [ ] \n",
        ),
        NoteTemplate(
            id = "proyek",
            title = "Rencana Proyek",
            description = "Perencanaan langkah demi langkah",
            category = TemplateCategory.KERJA,
            accent = AccentKey.ORANGE,
            body = "# Proyek\n\n## Tujuan\n\n## Langkah\n1. \n2. \n3. \n\n## Tenggat\n\n## Sumber daya\n- \n\n## Risiko\n- \n",
        ),
        NoteTemplate(
            id = "jurnal",
            title = "Jurnal Harian",
            description = "Refleksi dan perkembangan diri",
            category = TemplateCategory.PRIBADI,
            accent = AccentKey.GREEN,
            body = "# {tanggal}\n\n## Hal yang aku syukuri\n- \n\n## Yang terjadi hari ini\n\n## Yang aku pelajari\n- \n\n## Rencana besok\n- [ ] \n",
        ),
        NoteTemplate(
            id = "rencana_belajar",
            title = "Rencana Belajar",
            description = "Target, jadwal, dan sumber belajar",
            category = TemplateCategory.BELAJAR,
            accent = AccentKey.TEAL,
            body = "# Rencana Belajar\n\n## Target\n\n## Jadwal mingguan\n- Senin: \n- Rabu: \n- Jumat: \n\n## Sumber belajar\n- \n\n## Progres\n- [ ] \n",
        ),
    )

    fun byId(id: String): NoteTemplate? = all.firstOrNull { it.id == id }
}
