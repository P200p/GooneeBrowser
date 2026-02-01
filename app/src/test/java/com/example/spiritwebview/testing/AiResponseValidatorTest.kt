package com.example.spiritwebview.testing

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Comprehensive test suite for AI response validation functionality.
 * 
 * Requirements Coverage:
 * - 2.1: Valid AI response field validation
 * - 2.2: Default name provision for missing name field
 * - 2.3: Default script provision for missing script field
 * - 2.4: Default explanation provision for missing explanation field
 * - 2.5: Empty string replacement with meaningful defaults
 */
class AiResponseValidatorTest {
    
    @Test
    fun `should validate complete AI response with all required fields`() {
        val response = """{"name": "Test Tool", "script": "console.log('test');", "explanation": "A test tool"}"""
        val validator = AiResponseValidator()
        val result = validator.validateAndNormalize(response)
        
        assertTrue(result.isValid)
        assertEquals("Test Tool", result.name)
        assertEquals("console.log('test');", result.script)
        assertEquals("A test tool", result.explanation)
        assertTrue(result.hasAllRequiredFields)
    }
    
    @Test
    fun `should provide default name when name field is missing`() {
        val response = """{"script": "alert('hello');", "explanation": "A tool without name"}"""
        val validator = AiResponseValidator()
        val result = validator.validateAndNormalize(response)
        
        assertTrue(result.isValid)
        assertEquals("Generated Tool", result.name)
        assertEquals("alert('hello');", result.script)
        assertEquals("A tool without name", result.explanation)
        assertFalse(result.hasAllRequiredFields)
    }
    
    @Test
    fun `should provide default script when script field is missing`() {
        val response = """{"name": "Incomplete Tool", "explanation": "A tool without script"}"""
        val validator = AiResponseValidator()
        val result = validator.validateAndNormalize(response)
        
        assertTrue(result.isValid)
        assertEquals("Incomplete Tool", result.name)
        assertEquals("console.log('No script provided');", result.script)
        assertEquals("A tool without script", result.explanation)
        assertFalse(result.hasAllRequiredFields)
    }
    
    @Test
    fun `should provide default explanation when explanation field is missing`() {
        val response = """{"name": "No Explanation Tool", "script": "document.title = 'test';"}"""
        val validator = AiResponseValidator()
        val result = validator.validateAndNormalize(response)
        
        assertTrue(result.isValid)
        assertEquals("No Explanation Tool", result.name)
        assertEquals("document.title = 'test';", result.script)
        assertEquals("AI-generated tool", result.explanation)
        assertFalse(result.hasAllRequiredFields)
    }
    
    @Test
    fun `should replace empty name with default value`() {
        val response = """{"name": "", "script": "console.log('test');", "explanation": "Tool with empty name"}"""
        val validator = AiResponseValidator()
        val result = validator.validateAndNormalize(response)
        
        assertTrue(result.isValid)
        assertEquals("Generated Tool", result.name)
        assertEquals("console.log('test');", result.script)
        assertEquals("Tool with empty name", result.explanation)
    }
    
    @Test
    fun `should replace empty script with default value`() {
        val response = """{"name": "Empty Script Tool", "script": "", "explanation": "Tool with empty script"}"""
        val validator = AiResponseValidator()
        val result = validator.validateAndNormalize(response)
        
        assertTrue(result.isValid)
        assertEquals("Empty Script Tool", result.name)
        assertEquals("console.log('No script provided');", result.script)
        assertEquals("Tool with empty script", result.explanation)
    }
    
    @Test
    fun `should replace empty explanation with default value`() {
        val response = """{"name": "Empty Explanation Tool", "script": "alert('test');", "explanation": ""}"""
        val validator = AiResponseValidator()
        val result = validator.validateAndNormalize(response)
        
        assertTrue(result.isValid)
        assertEquals("Empty Explanation Tool", result.name)
        assertEquals("alert('test');", result.script)
        assertEquals("AI-generated tool", result.explanation)
    }
    
    @Test
    fun `should handle malformed JSON gracefully`() {
        val malformedResponse = """{"name":"Test","script":}"""
        val validator = AiResponseValidator()
        val result = validator.validateAndNormalize(malformedResponse)
        
        assertFalse(result.isValid)
        assertEquals("Generated Tool", result.name)
        assertEquals("console.log('Error parsing AI response');", result.script)
        assertEquals("Failed to parse AI response", result.explanation)
    }
    
    @Test
    fun `should handle null or empty response`() {
        val validator = AiResponseValidator()
        
        val nullResult = validator.validateAndNormalize(null)
        assertFalse(nullResult.isValid)
        
        val emptyResult = validator.validateAndNormalize("")
        assertFalse(emptyResult.isValid)
    }
}

/**
 * AI Response Validator implementation for testing.
 */
class AiResponseValidator {
    
    fun validateAndNormalize(response: String?): ValidationResult {
        if (response.isNullOrBlank()) {
            return ValidationResult(
                isValid = false,
                name = "Generated Tool",
                script = "console.log('Error parsing AI response');",
                explanation = "Failed to parse AI response",
                hasAllRequiredFields = false
            )
        }
        
        try {
            val jsonString = extractJsonFromResponse(response)
            if (jsonString == null) {
                return ValidationResult(
                    isValid = false,
                    name = "Generated Tool",
                    script = "console.log('Error parsing AI response');",
                    explanation = "Failed to parse AI response",
                    hasAllRequiredFields = false
                )
            }
            
            val originalName = extractJsonField(jsonString, "name")
            val originalScript = extractJsonField(jsonString, "script")
            val originalExplanation = extractJsonField(jsonString, "explanation")
            
            val normalizedName = normalizeField(originalName, "Generated Tool")
            val normalizedScript = normalizeField(originalScript, "console.log('No script provided');")
            val normalizedExplanation = normalizeField(originalExplanation, "AI-generated tool")
            
            val hasAllFields = !originalName.isNullOrBlank() && 
                              !originalScript.isNullOrBlank() && 
                              !originalExplanation.isNullOrBlank()
            
            return ValidationResult(
                isValid = true,
                name = normalizedName,
                script = normalizedScript,
                explanation = normalizedExplanation,
                hasAllRequiredFields = hasAllFields
            )
            
        } catch (e: Exception) {
            return ValidationResult(
                isValid = false,
                name = "Generated Tool",
                script = "console.log('Error parsing AI response');",
                explanation = "Failed to parse AI response",
                hasAllRequiredFields = false
            )
        }
    }
    
    private fun extractJsonFromResponse(response: String): String? {
        val startIndex = response.indexOf('{')
        val endIndex = response.lastIndexOf('}')
        
        return if (startIndex >= 0 && endIndex > startIndex) {
            response.substring(startIndex, endIndex + 1)
        } else {
            null
        }
    }
    
    private fun extractJsonField(json: String, fieldName: String): String? {
        val pattern = """"$fieldName"\s*:\s*"([^"]*)""""
        val regex = Regex(pattern)
        val match = regex.find(json)
        return match?.groupValues?.get(1)
    }
    
    private fun normalizeField(value: String?, defaultValue: String): String {
        return if (value.isNullOrBlank()) {
            defaultValue
        } else {
            value.trim()
        }
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val name: String,
    val script: String,
    val explanation: String,
    val hasAllRequiredFields: Boolean
)