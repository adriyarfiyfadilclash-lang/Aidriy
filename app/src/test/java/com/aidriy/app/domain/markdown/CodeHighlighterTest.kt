package com.aidriy.app.domain.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeHighlighterTest {
    private fun tokens(lang: String, code: String) =
        CodeHighlighter.highlight(lang, code).map { code.substring(it.start, it.end) to it.kind }

    @Test
    fun kotlin_keywords_strings_comments_numbers_types() {
        val t = tokens("kotlin", "val x: Int = 42 // jawab\nfun f() = \"hai \\\" kamu\"")
        assertTrue("val" to CodeTokenKind.KEYWORD in t)
        assertTrue("Int" to CodeTokenKind.TYPE in t)
        assertTrue("42" to CodeTokenKind.NUMBER in t)
        assertTrue("// jawab" to CodeTokenKind.COMMENT in t)
        assertTrue("\"hai \\\" kamu\"" to CodeTokenKind.STRING in t)
        assertTrue(t.none { it.first == "x" })
    }

    @Test
    fun python_hash_comments_and_sql_case_insensitive() {
        assertTrue("# catatan" to CodeTokenKind.COMMENT in tokens("py", "def f(): # catatan"))
        assertTrue("def" to CodeTokenKind.KEYWORD in tokens("python", "def f(): pass"))
        val sql = tokens("sql", "SELECT nama FROM notes -- semua")
        assertTrue("SELECT" to CodeTokenKind.KEYWORD in sql)
        assertTrue("-- semua" to CodeTokenKind.COMMENT in sql)
        assertTrue(sql.none { it.first == "nama" })
    }

    @Test
    fun unknown_language_only_strings_and_numbers() {
        val t = tokens("", "jalan 3 kali \"ok\" // bukan komentar")
        assertEquals(listOf("3" to CodeTokenKind.NUMBER, "\"ok\"" to CodeTokenKind.STRING), t)
    }

    @Test
    fun json_literals() {
        val t = tokens("json", "{\"a\": true, \"b\": -1.5}")
        assertTrue("true" to CodeTokenKind.KEYWORD in t)
        assertTrue("-1.5" to CodeTokenKind.NUMBER in t)
        assertTrue("\"a\"" to CodeTokenKind.STRING in t)
    }
}
