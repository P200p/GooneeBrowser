package com.example.spiritwebview

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Enhanced test suite for JsonUtils with comprehensive coverage.
 * 
 * This test class provides comprehensive testing for JSON extraction functionality:
 * - Unit tests for specific edge cases and error scenarios
 * - Tests for markdown formatting, nested objects, escaped strings
 * - Null safety and exception handling validation
 * - Performance tests for large inputs
 * 
 * Requirements Coverage:
 * - 1.1: JSON extraction with markdown formatting
 * - 1.2: JSON extraction with surrounding text
 * - 1.3: Nested JSON object handling
 * - 1.4: Escaped string preservation
 * - 1.5: Malformed JSON graceful handling
 * - 1.6: No JSON content null return
 */
class JsonUtilsTest {
    
    // ========== Original Unit Tests (Enhanced) ==========
    
    @Test
    fun extractSimpleObject() {
        val raw = "Here is some text {\"name\":\"Tool\",\"script\":\"alert('hi')\"} trailing"
        val extracted = JsonUtils.extractFirstJsonObject(raw)
        assertNotNull(extracted)
        assertTrue(extracted!!.startsWith("{"))
        assertTrue(extracted.endsWith("}"))
        assertTrue(extracted.contains("\"name\""))
    }

    @Test
    fun extractWithBracesInString() {
        val raw = "Prefix {\"text\":\"braces { inside string } still ok\", \"n\":1} suffix"
        val extracted = JsonUtils.extractFirstJsonObject(raw)
        assertNotNull(extracted)
        assertTrue(extracted!!.contains("braces { inside string }"))
    }

    @Test
    fun extractWhenNoJson() {
        val raw = "No json here: just text { not closed"
        val extracted = JsonUtils.extractFirstJsonObject(raw)
        assertNull(extracted)
    }

    @Test
    fun extractNestedObjects() {
        val raw = "start {\"a\": {\"b\": {\"c\": 1}}, \"x\": 2} end"
        val extracted = JsonUtils.extractFirstJsonObject(raw)
        assertNotNull(extracted)
        assertTrue(extracted!!.contains("\"c\": 1"))
    }

    @Test
    fun extractNullOrEmpty() {
        assertNull(JsonUtils.extractFirstJsonObject(null))
        assertNull(JsonUtils.extractFirstJsonObject(""))
    }
    
    // ========== Enhanced Unit Tests ==========
    
    @Test
    fun extractFromMarkdownCodeBlock() {
        val json = """{"name":"Test Tool","script":"console.log('test');","explanation":"A test script"}"""
        val markdown = """
            Here's your JavaScript tool:
            
            ```json
            $json
            ```
            
            This should work for your needs!
        """.trimIndent()
        val extracted = JsonUtils.extractFirstJsonObject(markdown)
        
        assertNotNull(extracted, "Should extract JSON from markdown")
        assertTrue(extracted!!.contains("\"name\""), "Should contain name field")
        assertTrue(extracted.contains("\"script\""), "Should contain script field")
        assertTrue(extracted.contains("\"explanation\""), "Should contain explanation field")
        assertEquals(json, extracted, "Should extract clean JSON")
    }
    
    @Test
    fun extractWithEscapedQuotes() {
        val raw = """Text before {"name":"Test","script":"alert(\"Hello World\")","explanation":"Shows escaped quotes"} text after"""
        val extracted = JsonUtils.extractFirstJsonObject(raw)
        
        assertNotNull(extracted, "Should extract JSON with escaped quotes")
        assertTrue(extracted!!.contains("\\\"Hello World\\\""), "Should preserve escaped quotes")
    }
    
    @Test
    fun extractFirstOfMultipleJsonObjects() {
        val raw = """First: {"name":"First","value":1} Second: {"name":"Second","value":2}"""
        val extracted = JsonUtils.extractFirstJsonObject(raw)
        
        assertNotNull(extracted, "Should extract first JSON object")
        assertTrue(extracted!!.contains("\"name\":\"First\""), "Should extract the first object")
        assertFalse(extracted.contains("\"name\":\"Second\""), "Should not include second object")
    }
    
    @Test
    fun extractWithSpecialCharactersInStrings() {
        val raw = """{"name":"Special","script":"console.log('Line 1\\nLine 2\\tTabbed')","explanation":"Contains \\n and \\t"}"""
        val extracted = JsonUtils.extractFirstJsonObject(raw)
        
        assertNotNull(extracted, "Should extract JSON with special characters")
        assertTrue(extracted!!.contains("\\n"), "Should preserve newline escape")
        assertTrue(extracted.contains("\\t"), "Should preserve tab escape")
    }
    
    @Test
    fun extractWithUnicodeCharacters() {
        val raw = """Text {"name":"Unicode Test","script":"alert('สวัสดี 🌟')","explanation":"Unicode support"} end"""
        val extracted = JsonUtils.extractFirstJsonObject(raw)
        
        assertNotNull(extracted, "Should extract JSON with Unicode")
        assertTrue(extracted!!.contains("สวัสดี 🌟"), "Should preserve Unicode characters")
    }
    
    // ========== Error Handling Tests ==========
    
    @Test
    fun handleMalformedJsonGracefully() {
        // Test cases that should return null (unmatched braces or no braces)
        val nullCases = listOf(
            """{"name":"test","script":"alert()"""", // Missing closing brace - unmatched
            """no braces here at all""", // No braces
            """just text { not closed properly""" // Unclosed brace
        )
        
        nullCases.forEach { nullCase ->
            val extracted = JsonUtils.extractFirstJsonObject(nullCase)
            assertNull(extracted, "Should return null for unmatched/missing braces: $nullCase")
        }
        
        // Test cases that are handled by the permissive brace-counting implementation
        // The implementation extracts anything with matched braces, regardless of JSON validity
        val handledCases = listOf(
            """{"name":"test","script":}""", // Missing value - but braces match
            """{name:"test","script":"alert()"}""", // Unquoted key - but braces match
            """{"name":"test""script":"alert()"}""", // Missing comma - but braces match
            """{"name":"test","script":"alert()",""}""", // Empty key - but braces match
            """{"name":"test","script":"alert()"}}""" // Extra brace - but first object has matched braces
        )
        
        handledCases.forEach { handled ->
            val extracted = JsonUtils.extractFirstJsonObject(handled)
            assertNotNull(extracted, "Implementation extracts anything with matched braces: $handled")
        }
    }
    
    @Test
    fun handleVeryLargeJsonObjects() {
        val largeValue = "x".repeat(10000)
        val raw = """{"name":"Large","script":"$largeValue","explanation":"Very large script"}"""
        val extracted = JsonUtils.extractFirstJsonObject(raw)
        
        assertNotNull(extracted, "Should handle large JSON objects")
        assertTrue(extracted!!.contains(largeValue), "Should contain large value")
    }
    
    @Test
    fun handleDeeplyNestedObjects() {
        val nested = (1..50).fold("1") { acc, _ -> "{\"nested\":$acc}" }
        val raw = """Text before $nested text after"""
        val extracted = JsonUtils.extractFirstJsonObject(raw)
        
        assertNotNull(extracted, "Should handle deeply nested objects")
        assertTrue(extracted!!.startsWith("{"), "Should start with opening brace")
        assertTrue(extracted.endsWith("}"), "Should end with closing brace")
    }
    
    @Test
    fun handleJsonWithArrays() {
        val raw = """{"name":"Array Test","items":["item1","item2",{"nested":"value"}],"script":"test"}"""
        val extracted = JsonUtils.extractFirstJsonObject(raw)
        
        assertNotNull(extracted, "Should extract JSON with arrays")
        assertTrue(extracted!!.contains("["), "Should contain array")
        assertTrue(extracted.contains("{\"nested\":\"value\"}"), "Should contain nested object in array")
    }
    
    // ========== Performance Tests ==========
    
    @Test
    fun performanceWithLargeText() {
        val largePrefix = "x".repeat(100000)
        val json = """{"name":"Large Test","script":"console.log('test');","explanation":"Performance test"}"""
        val raw = "$largePrefix$json"
        
        val startTime = System.currentTimeMillis()
        val extracted = JsonUtils.extractFirstJsonObject(raw)
        val endTime = System.currentTimeMillis()
        
        assertNotNull(extracted, "Should extract from large text")
        assertTrue((endTime - startTime) < 1000, "Should complete within reasonable time")
    }
    
    // ========== Additional Comprehensive Edge Cases ==========
    
    @Test
    fun extractFromMultipleMarkdownBlocks() {
        val raw = """
            First block:
            ```json
            {"name":"First","script":"alert('1')"}
            ```
            
            Second block:
            ```json
            {"name":"Second","script":"alert('2')"}
            ```
        """.trimIndent()
        
        val extracted = JsonUtils.extractFirstJsonObject(raw)
        assertNotNull(extracted, "Should extract first JSON")
        assertTrue(extracted!!.contains("\"name\":\"First\""), "Should extract first block")
        assertFalse(extracted.contains("\"name\":\"Second\""), "Should not include second block")
    }
    
    @Test
    fun extractWithComplexNestedStructures() {
        val raw = """
            {
                "name": "Complex Tool",
                "config": {
                    "settings": {
                        "enabled": true,
                        "options": ["opt1", "opt2", {"nested": "value"}]
                    },
                    "metadata": {
                        "version": "1.0",
                        "author": {"name": "Test", "email": "test@example.com"}
                    }
                },
                "script": "console.log('complex');",
                "explanation": "A complex nested structure"
            }
        """.trimIndent()
        
        val extracted = JsonUtils.extractFirstJsonObject(raw)
        assertNotNull(extracted, "Should extract complex nested JSON")
        assertTrue(extracted!!.contains("\"settings\""), "Should preserve nested structures")
        assertTrue(extracted.contains("[\"opt1\", \"opt2\""), "Should preserve arrays")
        assertTrue(extracted.contains("{\"nested\": \"value\"}"), "Should preserve nested objects in arrays")
    }
    
    @Test
    fun extractWithVariousStringEscapes() {
        val raw = """{"name":"Escape Test","script":"alert(\"Hello\\nWorld\\t!\");","explanation":"Contains \\\"quotes\\\", \\n newlines, \\t tabs, and \\\\ backslashes"}"""
        val extracted = JsonUtils.extractFirstJsonObject(raw)
        
        assertNotNull(extracted, "Should extract JSON with various escapes")
        assertTrue(extracted!!.contains("\\\"Hello"), "Should preserve escaped quotes")
        assertTrue(extracted.contains("\\n"), "Should preserve newline escape")
        assertTrue(extracted.contains("\\t"), "Should preserve tab escape")
        assertTrue(extracted.contains("\\\\"), "Should preserve backslash escape")
    }
    
    @Test
    fun extractWithJsonInDifferentLanguageContexts() {
        val testCases = listOf(
            // Thai context
            "นี่คือ JSON สำหรับคุณ: {\"name\":\"เครื่องมือไทย\",\"script\":\"alert('สวัสดี')\"} ใช้งานได้เลย",
            // Chinese context  
            "这是您的JSON: {\"name\":\"中文工具\",\"script\":\"console.log('你好')\"} 请测试",
            // Japanese context
            "JSONは次の通りです: {\"name\":\"日本のツール\",\"script\":\"alert('こんにちは')\"} どうぞ",
            // Arabic context (RTL)
            "هذا هو JSON الخاص بك: {\"name\":\"أداة عربية\",\"script\":\"alert('مرحبا')\"} جرب هذا"
        )
        
        testCases.forEach { testCase ->
            val extracted = JsonUtils.extractFirstJsonObject(testCase)
            assertNotNull(extracted, "Should extract JSON from multilingual context: $testCase")
            assertTrue(extracted!!.startsWith("{"), "Should start with brace")
            assertTrue(extracted.endsWith("}"), "Should end with brace")
        }
    }
    
    @Test
    fun extractWithMixedQuoteTypes() {
        // Test with mixed single and double quotes
        val validJson = """{"name":"Valid","script":"alert('single quotes inside')"}"""
        val singleQuoteJson = """{'name':'SingleQuote','script':'alert("double quotes inside")'}"""
        
        val validExtracted = JsonUtils.extractFirstJsonObject(validJson)
        val singleQuoteExtracted = JsonUtils.extractFirstJsonObject(singleQuoteJson)
        
        assertNotNull(validExtracted, "Should extract valid JSON with double quotes")
        // The implementation is permissive and extracts based on brace counting, not quote validation
        assertNotNull(singleQuoteExtracted, "Implementation extracts single-quote JSON based on brace structure")
        assertTrue(singleQuoteExtracted!!.contains("'name':'SingleQuote'"), "Should preserve single quotes")
    }
    
    @Test
    fun extractWithJsonContainingUrls() {
        val raw = """{"name":"URL Test","script":"window.open('https://example.com/path?param=value&other=123')","explanation":"Opens https://example.com"}"""
        val extracted = JsonUtils.extractFirstJsonObject(raw)
        
        assertNotNull(extracted, "Should extract JSON containing URLs")
        assertTrue(extracted!!.contains("https://example.com/path?param=value&other=123"), "Should preserve URL with parameters")
    }
    
    @Test
    fun extractWithJsonContainingRegex() {
        val raw = """{"name":"Regex Test","script":"text.replace(/[{}]/g, '')","explanation":"Uses regex to remove braces"}"""
        val extracted = JsonUtils.extractFirstJsonObject(raw)
        
        assertNotNull(extracted, "Should extract JSON containing regex")
        assertTrue(extracted!!.contains("/[{}]/g"), "Should preserve regex pattern")
    }
    
    @Test
    fun extractWithEmptyStringValues() {
        val raw = """{"name":"","script":"","explanation":""}"""
        val extracted = JsonUtils.extractFirstJsonObject(raw)
        
        assertNotNull(extracted, "Should extract JSON with empty strings")
        assertTrue(extracted!!.contains("\"name\":\"\""), "Should preserve empty name")
        assertTrue(extracted.contains("\"script\":\"\""), "Should preserve empty script")
        assertTrue(extracted.contains("\"explanation\":\"\""), "Should preserve empty explanation")
    }
    
    @Test
    fun extractWithNumericAndBooleanValues() {
        val raw = """{"name":"Mixed Types","version":1.5,"enabled":true,"count":42,"active":false,"data":null}"""
        val extracted = JsonUtils.extractFirstJsonObject(raw)
        
        assertNotNull(extracted, "Should extract JSON with mixed value types")
        assertTrue(extracted!!.contains("1.5"), "Should preserve decimal number")
        assertTrue(extracted.contains("true"), "Should preserve boolean true")
        assertTrue(extracted.contains("42"), "Should preserve integer")
        assertTrue(extracted.contains("false"), "Should preserve boolean false")
        assertTrue(extracted.contains("null"), "Should preserve null")
    }
    
    @Test
    fun extractWithTrailingCommas() {
        // JSON with trailing commas (technically invalid but common)
        val raw = """{"name":"Trailing","script":"test",}"""
        val extracted = JsonUtils.extractFirstJsonObject(raw)
        
        // The current implementation should still extract this as it's brace-counting based
        assertNotNull(extracted, "Should extract JSON even with trailing comma")
        assertTrue(extracted!!.contains(",}"), "Should include the trailing comma")
    }
    
    @Test
    fun extractFromAiResponseWithExplanation() {
        val raw = """
            I'll create a tool for you. Here's the JSON:
            
            ```json
            {
                "name": "Page Translator",
                "script": "const elements = document.querySelectorAll('p, h1, h2, h3, span'); elements.forEach(el => { /* translation logic */ });",
                "explanation": "This tool translates all text elements on the page using a translation service."
            }
            ```
            
            This tool will help you translate web pages automatically.
        """.trimIndent()
        
        val extracted = JsonUtils.extractFirstJsonObject(raw)
        assertNotNull(extracted, "Should extract JSON from AI response")
        assertTrue(extracted!!.contains("translation logic"), "Should contain translation logic")
        assertTrue(extracted.contains("This tool translates"), "Should contain explanation")
    }
    
    @Test
    fun extractWithJsonContainingCodeSnippets() {
        val raw = """{"name":"Code Injector","script":"const code = `function test() { return {success: true}; }`; eval(code);","explanation":"Injects and executes code"}"""
        val extracted = JsonUtils.extractFirstJsonObject(raw)
        
        assertNotNull(extracted, "Should extract JSON containing code snippets")
        assertTrue(extracted!!.contains("`function test()"), "Should preserve template literals")
        assertTrue(extracted.contains("{success: true}"), "Should preserve nested braces in code")
    }
}