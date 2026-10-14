package com.aidriy.app.ui.ai

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.ui.graphics.vector.ImageVector
import com.aidriy.app.ui.theme.AccentKey

enum class SuggestionCategory(val label: String) {
    CHAT("Chat"), ANALYSIS("Analisis"), SUMMARY("Ringkas"), TRANSLATE("Terjemah"), CREATE("Buat")
}

/** What a suggestion needs before it can be sent. */
enum class SuggestionInput { NONE, NOTE, DOCUMENT }

data class AiSuggestion(
    val id: String,
    val title: String,
    /** Text placed in the input box; the user finishes or sends it. */
    val prompt: String,
    val category: SuggestionCategory,
    val input: SuggestionInput,
    val accent: AccentKey,
    val icon: ImageVector,
)

object AiSuggestions {
    val all = listOf(
        AiSuggestion(
            "summarize_note", "Ringkas catatan ini", "Ringkas catatan ini menjadi poin-poin penting.",
            SuggestionCategory.SUMMARY, SuggestionInput.NOTE, AccentKey.GREEN, Icons.Outlined.Summarize,
        ),
        AiSuggestion(
            "study_plan", "Buat rencana belajar", "Buatkan rencana belajar 4 minggu untuk topik: ",
            SuggestionCategory.CREATE, SuggestionInput.NONE, AccentKey.RED, Icons.Outlined.School,
        ),
        AiSuggestion(
            "explain", "Jelaskan konsep ini", "Jelaskan konsep berikut dengan bahasa sederhana: ",
            SuggestionCategory.CHAT, SuggestionInput.NONE, AccentKey.BLUE, Icons.Outlined.Lightbulb,
        ),
        AiSuggestion(
            "ideas", "Buat ide kreatif", "Berikan 10 ide kreatif untuk: ",
            SuggestionCategory.CREATE, SuggestionInput.NONE, AccentKey.PURPLE, Icons.Outlined.Psychology,
        ),
        AiSuggestion(
            "analyze_doc", "Analisis dokumen",
            "Analisis dokumen ini: ringkasan, poin utama, dan hal yang perlu ditindaklanjuti.",
            SuggestionCategory.ANALYSIS, SuggestionInput.DOCUMENT, AccentKey.RED, Icons.Outlined.Description,
        ),
        AiSuggestion(
            "translate", "Terjemahkan teks", "Terjemahkan teks berikut ke bahasa Inggris:\n",
            SuggestionCategory.TRANSLATE, SuggestionInput.NONE, AccentKey.TEAL, Icons.Outlined.Translate,
        ),
        AiSuggestion(
            "mind_map", "Buat mind map", "Buat mind map berupa daftar bertingkat untuk topik: ",
            SuggestionCategory.CREATE, SuggestionInput.NONE, AccentKey.BLUE, Icons.Outlined.AccountTree,
        ),
        AiSuggestion(
            "term", "Jelaskan istilah ini", "Jelaskan istilah berikut beserta contohnya: ",
            SuggestionCategory.CHAT, SuggestionInput.NONE, AccentKey.RED, Icons.Outlined.HelpOutline,
        ),
        AiSuggestion(
            "tasks", "Buat daftar tugas", "Ubah teks berikut menjadi daftar tugas yang jelas:\n",
            SuggestionCategory.CREATE, SuggestionInput.NONE, AccentKey.YELLOW, Icons.Outlined.TaskAlt,
        ),
        AiSuggestion(
            "analyze_note", "Analisis catatan", "Analisis catatan ini: apa yang kurang, apa yang bisa diperjelas?",
            SuggestionCategory.ANALYSIS, SuggestionInput.NOTE, AccentKey.ORANGE, Icons.Outlined.Description,
        ),
    )

    /** The six cards on the empty chat screen. */
    val home = listOf("summarize_note", "study_plan", "explain", "ideas", "analyze_doc", "translate").map { id -> all.first { it.id == id } }

    fun byId(id: String) = all.firstOrNull { it.id == id }
}
