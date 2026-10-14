package com.aidriy.app.ui.theme

import androidx.compose.ui.graphics.Color

// Brand blue (matches the reference design's accent)
internal val Blue600 = Color(0xFF2563EB)
internal val Blue500 = Color(0xFF3B82F6)
internal val Blue400 = Color(0xFF60A5FA)
internal val Blue50 = Color(0xFFEAF1FE)
internal val Blue900 = Color(0xFF0B2A6B)

// Light neutrals
internal val Neutral0 = Color(0xFFFFFFFF)
internal val Neutral25 = Color(0xFFF6F8FB)
internal val Neutral50 = Color(0xFFF0F3F8)
internal val Neutral100 = Color(0xFFE6EAF1)
internal val Neutral200 = Color(0xFFDCE1EA)
internal val Neutral500 = Color(0xFF5B6472) // 5.8:1 on Neutral25 (WCAG AA)
internal val Neutral900 = Color(0xFF0F141B)

// Dark neutrals
internal val Dark0 = Color(0xFF0E1218)
internal val Dark50 = Color(0xFF151A22)
internal val Dark100 = Color(0xFF1C222C)
internal val Dark200 = Color(0xFF262D39)
internal val Dark300 = Color(0xFF323A48)
internal val DarkText = Color(0xFFE8ECF2)
internal val DarkMuted = Color(0xFF9AA4B2)
internal val DarkPrimaryContainer = Color(0xFF1B2A45)
internal val DarkOnPrimaryContainer = Color(0xFFD6E3FF)
internal val DarkErrorContainer = Color(0xFF3A1F22)

// Feedback
internal val Red600 = Color(0xFFDC3B2F)
internal val Red50 = Color(0xFFFDECEA)
internal val RedDark = Color(0xFFFF8A80)

/** Soft tile + strong icon color pairs used for quick actions, folders and list icons. */
data class Accent(val container: Color, val content: Color)

object Accents {
    val Blue = Accent(Color(0xFFE6EFFE), Color(0xFF2563EB))
    val Red = Accent(Color(0xFFFDE8E8), Color(0xFFE5484D))
    val Green = Accent(Color(0xFFE3F7EC), Color(0xFF16A35A))
    val Orange = Accent(Color(0xFFFFEEDF), Color(0xFFF07A16))
    val Purple = Accent(Color(0xFFF0E9FE), Color(0xFF7C4DFF))
    val Yellow = Accent(Color(0xFFFFF4D6), Color(0xFFE5A50A))
    val Teal = Accent(Color(0xFFE0F5F5), Color(0xFF0E9AA7))

    val DarkBlue = Accent(Color(0xFF1B2A45), Color(0xFF7CA7FF))
    val DarkRed = Accent(Color(0xFF3A1F22), Color(0xFFFF8A8E))
    val DarkGreen = Accent(Color(0xFF15301F), Color(0xFF5FD38D))
    val DarkOrange = Accent(Color(0xFF3A2A18), Color(0xFFFFAF66))
    val DarkPurple = Accent(Color(0xFF2B2240), Color(0xFFB69CFF))
    val DarkYellow = Accent(Color(0xFF39321A), Color(0xFFFFD25E))
    val DarkTeal = Accent(Color(0xFF14302F), Color(0xFF5AD1D8))

    val light = listOf(Blue, Orange, Red, Green, Purple, Yellow, Teal)
    val dark = listOf(DarkBlue, DarkOrange, DarkRed, DarkGreen, DarkPurple, DarkYellow, DarkTeal)
}

enum class AccentKey { BLUE, ORANGE, RED, GREEN, PURPLE, YELLOW, TEAL }
