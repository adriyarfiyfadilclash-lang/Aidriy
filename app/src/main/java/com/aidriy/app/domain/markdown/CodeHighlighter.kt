package com.aidriy.app.domain.markdown

enum class CodeTokenKind { KEYWORD, STRING, COMMENT, NUMBER, TYPE }

/** [start, end) range of [kind] inside a code block. */
data class CodeSpan(val start: Int, val end: Int, val kind: CodeTokenKind)

/**
 * Small, dependency-free syntax highlighter for code blocks in notes and AI answers. It only
 * colours tokens (keywords, strings, comments, numbers, capitalised types); it never changes text.
 */
object CodeHighlighter {

    private enum class Family { C_LIKE, HASH, SQL, JSON, MARKUP, PLAIN }

    private val cKeywords = setOf(
        // Kotlin / Java / C# / Swift / Dart
        "abstract", "as", "break", "case", "catch", "class", "const", "continue", "data", "default", "do", "else",
        "enum", "extends", "false", "final", "finally", "for", "fun", "func", "if", "implements", "import", "in",
        "interface", "internal", "is", "let", "new", "null", "nil", "object", "open", "override", "package",
        "private", "protected", "public", "return", "sealed", "static", "super", "suspend", "switch", "this",
        "throw", "throws", "true", "try", "typealias", "val", "var", "void", "when", "while", "with", "struct",
        "namespace", "using", "async", "await", "yield", "where", "init", "guard", "extension", "protocol",
        // JavaScript / TypeScript
        "function", "export", "from", "typeof", "instanceof", "undefined", "type", "declare", "readonly", "of",
        // C / C++ / Go / Rust
        "int", "long", "short", "char", "float", "double", "bool", "boolean", "unsigned", "signed", "sizeof",
        "include", "define", "go", "chan", "defer", "map", "range", "select", "fn", "impl", "mut", "pub", "use",
        "mod", "crate", "trait", "match", "loop", "self", "Self", "string", "auto", "template", "virtual",
    )

    private val hashKeywords = setOf(
        // Python
        "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif", "else",
        "except", "False", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda", "None",
        "nonlocal", "not", "or", "pass", "raise", "return", "True", "try", "while", "with", "yield", "print",
        "self",
        // Ruby
        "begin", "end", "module", "require", "unless", "until", "nil", "true", "false", "then", "do",
        // Shell
        "fi", "esac", "done", "case", "echo", "export", "local", "function", "sudo", "cd",
    )

    private val sqlKeywords = setOf(
        "select", "from", "where", "and", "or", "not", "insert", "into", "values", "update", "set", "delete",
        "create", "table", "drop", "alter", "add", "primary", "key", "foreign", "references", "join", "left",
        "right", "inner", "outer", "on", "group", "by", "order", "having", "limit", "offset", "as", "distinct",
        "null", "is", "in", "like", "between", "union", "all", "case", "when", "then", "else", "end", "index",
        "view", "default", "unique", "count", "sum", "avg", "min", "max", "asc", "desc", "exists", "begin",
        "commit", "rollback", "integer", "text", "varchar", "boolean", "true", "false",
    )

    private val jsonKeywords = setOf("true", "false", "null")

    private fun family(language: String): Family = when (language.trim().lowercase()) {
        "kotlin", "kt", "kts", "java", "js", "javascript", "jsx", "ts", "typescript", "tsx", "c", "h", "cpp", "c++",
        "cs", "csharp", "go", "golang", "rust", "rs", "swift", "dart", "php", "scala", "groovy", "gradle" -> Family.C_LIKE
        "python", "py", "ruby", "rb", "bash", "sh", "shell", "zsh", "yaml", "yml", "toml", "r", "perl", "powershell", "ps1" -> Family.HASH
        "sql", "sqlite", "postgres", "postgresql", "mysql" -> Family.SQL
        "json", "jsonc" -> Family.JSON
        "html", "xml", "svg", "vue" -> Family.MARKUP
        else -> Family.PLAIN
    }

    private val number = """\b(?:0[xX][0-9a-fA-F_]+|\d[\d_]*(?:\.\d+)?(?:[eE][+-]?\d+)?[fFLdD]?)\b"""
    private val word = """[A-Za-z_][A-Za-z0-9_]*"""
    private val dq = """"(?:[^"\\\n]|\\.)*"?"""
    private val sq = """'(?:[^'\\\n]|\\.)*'"""

    private val regexes = mapOf(
        Family.C_LIKE to Regex("""(//[^\n]*|/\*[\s\S]*?(?:\*/|$))|($dq|$sq|`(?:[^`\\]|\\.)*`?)|($number)|($word)"""),
        Family.HASH to Regex("""(#[^\n]*)|(""${'"'}[\s\S]*?(?:""${'"'}|$)|$dq|$sq)|($number)|($word)"""),
        Family.SQL to Regex("""(--[^\n]*|/\*[\s\S]*?(?:\*/|$))|($sq|$dq)|($number)|($word)"""),
        Family.JSON to Regex("""(\u0000)|($dq)|(-?$number)|($word)"""),
        Family.MARKUP to Regex("""(<!--[\s\S]*?(?:-->|$))|($dq|$sq)|(\u0000)|(</?[A-Za-z][\w:-]*)"""),
        Family.PLAIN to Regex("""(\u0000)|($dq)|($number)|(\u0000)"""),
    )

    fun highlight(language: String, code: String): List<CodeSpan> {
        if (code.length > MAX_CHARS) return emptyList()
        val fam = family(language)
        val re = regexes.getValue(fam)
        val spans = mutableListOf<CodeSpan>()
        for (m in re.findAll(code)) {
            val g = m.groups
            val kind = when {
                g[1] != null -> CodeTokenKind.COMMENT
                g[2] != null -> CodeTokenKind.STRING
                g[3] != null -> CodeTokenKind.NUMBER
                g[4] != null -> wordKind(fam, m.value) ?: continue
                else -> continue
            }
            spans += CodeSpan(m.range.first, m.range.last + 1, kind)
        }
        return spans
    }

    private fun wordKind(fam: Family, w: String): CodeTokenKind? = when (fam) {
        Family.C_LIKE -> when {
            w in cKeywords -> CodeTokenKind.KEYWORD
            w.length > 1 && w[0].isUpperCase() && w.any { it.isLowerCase() } -> CodeTokenKind.TYPE
            else -> null
        }
        Family.HASH -> when {
            w in hashKeywords -> CodeTokenKind.KEYWORD
            w.length > 1 && w[0].isUpperCase() && w.any { it.isLowerCase() } -> CodeTokenKind.TYPE
            else -> null
        }
        Family.SQL -> if (w.lowercase() in sqlKeywords) CodeTokenKind.KEYWORD else null
        Family.JSON -> if (w in jsonKeywords) CodeTokenKind.KEYWORD else null
        Family.MARKUP -> CodeTokenKind.KEYWORD
        Family.PLAIN -> null
    }

    private const val MAX_CHARS = 20_000
}
