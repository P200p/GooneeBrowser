package com.example.spiritwebview

/**
 * Utilities for extracting JSON from free-form text.
 */
object JsonUtils {
    /**
     * Extract the first JSON object substring from raw text.
     * Returns the substring including surrounding braces, or null if none found.
     * This implementation skips braces that appear inside JSON strings.
     */
    fun extractFirstJsonObject(raw: String?): String? {
        if (raw.isNullOrEmpty()) return null
        val text = raw
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val ch = text[i]
            if (inString) {
                if (escape) {
                    escape = false
                } else {
                    if (ch == '\\') escape = true
                    else if (ch == '"') inString = false
                }
            } else {
                if (ch == '"') {
                    inString = true
                } else if (ch == '{') {
                    depth++
                } else if (ch == '}') {
                    depth--
                    if (depth == 0) {
                        return text.substring(start, i + 1)
                    }
                }
            }
        }
        return null
    }
}
