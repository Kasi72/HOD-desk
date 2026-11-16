package com.hoddesk.app.reasoning

/** Small dependency-free JSON reader for untrusted model responses. No coercion or duplicate keys. */
internal object StrictJson {
    data class Number(val token: String)

    fun objectValue(text: String): Map<String, Any?> {
        require(text.length <= 64_000) { "Reasoning response is too large" }
        return Reader(text).readObject()
    }

    private class Reader(private val text: String) {
        private var i = 0
        fun readObject(): Map<String, Any?> {
            val value = value(0)
            whitespace()
            require(i == text.length && value is Map<*, *>) { "Expected one JSON object" }
            @Suppress("UNCHECKED_CAST")
            return value as Map<String, Any?>
        }

        private fun value(depth: Int): Any? {
            require(depth <= 16) { "JSON nesting is too deep" }
            whitespace()
            require(i < text.length) { "Incomplete JSON" }
            return when (text[i]) {
                '{' -> {
                    i++
                    val result = linkedMapOf<String, Any?>()
                    whitespace()
                    if (take('}')) result else {
                        do {
                            whitespace()
                            require(i < text.length && text[i] == '"') { "Expected JSON key" }
                            val key = string()
                            require(!result.containsKey(key)) { "Duplicate JSON key: $key" }
                            whitespace(); require(take(':')) { "Missing JSON colon" }
                            result[key] = value(depth + 1)
                            whitespace()
                            if (take('}')) break
                            require(take(',')) { "Missing JSON comma" }
                        } while (true)
                        result
                    }
                }
                '[' -> {
                    i++
                    val result = mutableListOf<Any?>()
                    whitespace()
                    if (take(']')) result else {
                        do {
                            require(result.size < 100) { "JSON array is too large" }
                            result += value(depth + 1)
                            whitespace()
                            if (take(']')) break
                            require(take(',')) { "Missing JSON comma" }
                        } while (true)
                        result
                    }
                }
                '"' -> string()
                'n' -> literal("null", null)
                't' -> literal("true", true)
                'f' -> literal("false", false)
                '-', in '0'..'9' -> number()
                else -> throw IllegalArgumentException("Invalid JSON value")
            }
        }

        private fun string(): String {
            require(take('"'))
            val result = StringBuilder()
            while (i < text.length) {
                val ch = text[i++]
                if (ch == '"') return result.toString()
                require(ch.code >= 0x20) { "Unescaped control character" }
                if (ch != '\\') result.append(ch) else {
                    require(i < text.length) { "Incomplete escape" }
                    result.append(when (val escaped = text[i++]) {
                        '"', '\\', '/' -> escaped
                        'b' -> '\b'; 'f' -> '\u000c'; 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'
                        'u' -> {
                            require(i + 4 <= text.length) { "Incomplete Unicode escape" }
                            val code = text.substring(i, i + 4).toIntOrNull(16)
                            require(code != null) { "Invalid Unicode escape" }
                            i += 4; code.toChar()
                        }
                        else -> throw IllegalArgumentException("Invalid JSON escape")
                    })
                }
            }
            throw IllegalArgumentException("Unterminated JSON string")
        }

        private fun number(): Number {
            val start = i
            take('-')
            require(i < text.length)
            if (!take('0')) {
                require(text[i] in '1'..'9')
                while (i < text.length && text[i].isDigit()) i++
            }
            if (take('.')) digits()
            if (i < text.length && text[i] in "eE") { i++; if (i < text.length && text[i] in "+-") i++; digits() }
            return Number(text.substring(start, i))
        }
        private fun digits() { val start = i; while (i < text.length && text[i].isDigit()) i++; require(i > start) { "Invalid JSON number" } }
        private fun literal(token: String, result: Any?): Any? { require(text.startsWith(token, i)) { "Invalid JSON literal" }; i += token.length; return result }
        private fun take(ch: Char): Boolean = if (i < text.length && text[i] == ch) { i++; true } else false
        private fun whitespace() { while (i < text.length && text[i] in " \t\r\n") i++ }
    }
}
