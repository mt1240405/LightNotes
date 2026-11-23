package com.lightnotes.app.markdown

import io.noties.prism4j.GrammarLocator
import io.noties.prism4j.Prism4j
import io.noties.prism4j.Prism4j.grammar
import io.noties.prism4j.Prism4j.pattern
import io.noties.prism4j.Prism4j.token
import java.util.regex.Pattern

class LightNotesGrammarLocator : GrammarLocator {

    private val grammars = mutableMapOf<String, Prism4j.Grammar>()

    init {
        // Generic C-style grammar for Kotlin, Java, C, C++, JS, TS
        val cStyleGrammar = grammar(
            "clike",
            token(
                "comment",
                pattern(Pattern.compile("//.*")),
                pattern(Pattern.compile("/\\*[\\s\\S]*?\\*/"))
            ),
            token(
                "string",
                pattern(Pattern.compile("\"(?:\\\\.|[^\\\\\"\\r\\n])*\"")),
                pattern(Pattern.compile("'(?:\\\\.|[^\\\\'\\r\\n])*'"))
            ),
            token(
                "keyword",
                pattern(Pattern.compile("\\b(?:val|var|fun|class|interface|object|enum|public|private|protected|internal|import|package|return|if|else|when|for|while|do|try|catch|finally|throw|new|this|super|null|true|false|void|int|long|float|double|boolean|char|const|auto|struct|static|async|await|function|let|const|def|from|as)\\b"))
            ),
            token(
                "number",
                pattern(Pattern.compile("\\b0x[\\da-fA-F]+\\b|\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFL]?\\b"))
            ),
            token(
                "function",
                pattern(Pattern.compile("\\b[a-zA-Z_]\\w*(?=\\s*\\()"))
            ),
            token(
                "operator",
                pattern(Pattern.compile("[-+*/%=<>!&|^~?:]+"))
            )
        )

        // Python & Bash grammar
        val scriptGrammar = grammar(
            "script",
            token("comment", pattern(Pattern.compile("#.*"))),
            token(
                "string",
                pattern(Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"")),
                pattern(Pattern.compile("'''[\\s\\S]*?'''")),
                pattern(Pattern.compile("\"(?:\\\\.|[^\\\\\"\\r\\n])*\"")),
                pattern(Pattern.compile("'(?:\\\\.|[^\\\\'\\r\\n])*'"))
            ),
            token(
                "keyword",
                pattern(Pattern.compile("\\b(?:def|class|if|elif|else|while|for|in|try|except|finally|with|as|import|from|return|yield|lambda|pass|break|continue|None|True|False|and|or|not|is|echo|export|source|exit|sudo|sh|bash)\\b"))
            ),
            token(
                "number",
                pattern(Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b"))
            )
        )

        // SQL grammar
        val sqlGrammar = grammar(
            "sql",
            token("comment", pattern(Pattern.compile("--.*")), pattern(Pattern.compile("/\\*[\\s\\S]*?\\*/"))),
            token("string", pattern(Pattern.compile("'(?:''|[^'])*'"))),
            token(
                "keyword",
                pattern(Pattern.compile("(?i)\\b(?:SELECT|FROM|WHERE|INSERT|INTO|UPDATE|DELETE|JOIN|LEFT|RIGHT|INNER|OUTER|ON|GROUP|BY|ORDER|HAVING|LIMIT|CREATE|TABLE|DROP|ALTER|INDEX|PRIMARY|KEY|FOREIGN|REFERENCES|NOT|NULL|AND|OR|AS|IN|EXISTS|BETWEEN|LIKE|UNION|ALL|DISTINCT|CASE|WHEN|THEN|ELSE|END)\\b"))
            ),
            token("number", pattern(Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b")))
        )

        // JSON grammar
        val jsonGrammar = grammar(
            "json",
            token("property", pattern(Pattern.compile("\"(?:\\\\.|[^\\\\\"\\r\\n])*\"(?=\\s*:)"))),
            token("string", pattern(Pattern.compile("\"(?:\\\\.|[^\\\\\"\\r\\n])*\""))),
            token("number", pattern(Pattern.compile("\\b-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b"))),
            token("boolean", pattern(Pattern.compile("\\b(?:true|false|null)\\b")))
        )

        // XML / HTML grammar
        val markupGrammar = grammar(
            "markup",
            token("comment", pattern(Pattern.compile("<!--[\\s\\S]*?-->"))),
            token("tag", pattern(Pattern.compile("</?[a-zA-Z0-9:-]+"))),
            token("attr-name", pattern(Pattern.compile("\\b[a-zA-Z0-9:-]+(?=\\=)"))),
            token("string", pattern(Pattern.compile("\"[^\"]*\"|'[^']*'")))
        )

        grammars["clike"] = cStyleGrammar
        grammars["kotlin"] = cStyleGrammar
        grammars["kt"] = cStyleGrammar
        grammars["java"] = cStyleGrammar
        grammars["c"] = cStyleGrammar
        grammars["cpp"] = cStyleGrammar
        grammars["javascript"] = cStyleGrammar
        grammars["js"] = cStyleGrammar
        grammars["typescript"] = cStyleGrammar
        grammars["ts"] = cStyleGrammar

        grammars["python"] = scriptGrammar
        grammars["py"] = scriptGrammar
        grammars["bash"] = scriptGrammar
        grammars["sh"] = scriptGrammar

        grammars["sql"] = sqlGrammar
        grammars["json"] = jsonGrammar
        grammars["xml"] = markupGrammar
        grammars["html"] = markupGrammar
    }

    override fun grammar(prism4j: Prism4j, language: String): Prism4j.Grammar? {
        return grammars[language.lowercase()] ?: grammars["clike"]
    }

    override fun languages(): Set<String> {
        return grammars.keys
    }
}
