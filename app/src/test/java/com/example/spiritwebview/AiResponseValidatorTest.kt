package com.example.spiritwebview

import com.example.spiritwebview.testing.MockResponse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.json.JSONObject

/**
 * Comprehensive test suite for AI response validation functionality.
 * 
 * This test class validates the AI response processing logic that ensures proper handling
 * of AI responses with missing or empty fields, and validates that appropriate defaults
 * are provided according to the requirements.
 * 
 * Requirements Coverage:
 * - 2.1: Valid AI response field validation (name, script, explanation)
 * - 2.2: Default name provision for missing name field
 * - 2.3: Default error script provision for missing script field
 * - 2.4: Default explanation provision for missing explanation field
 * - 2.5: Empty string replacement with meaningful defaults
 * 
 * Test Strategy:
 * - Unit tests for specific validation scenarios and edge cases
 * - Comprehensive coverage of all field combinations (present/missing/empty)
 * - Default value validation for each field type
 * - Error handling validation for malformed responses
 * - Integration with JsonUtils for response parsing
 */
class AiResponseValidatorTest {

    /**
     * Data class representing a validated AI response with all required fields.
     * This mirrors the expected structure after validation and default value application.
     */
    data class ValidatedAiResponse(
        val name: String,
        val script: String,
        val explanation: String,
        val isValid: Boolean = true
    )

    /**
     * AI Response Validator that mimics the validation logic from BrowserActivity.
     * This extracts and centralizes the validation logic for comprehensive testing.
     */
    class AiResponseValidator {
        
        companion object {
            // Default values that should be provided when fields are missing or empty
            const val DEFAULT_NAME = "AI Tool"
            const val DEFAULT_SCRIPT = "alert('No script generated')"
            const val DEFAULT_EXPLANATION = "ไม่มีคำอธิบาย"
            const val ERROR_SCRIPT = "alert('AI generated script error')"
            const val JSON_ERROR_EXPLANATION = "ขอโทษทีครับ เฮียเอ๋อไปนิด ลองสั่งใหม่นะ (JSON Error)"
        }

        /**
         * Validates and processes an AI response, providing defaults for missing or empty fields.
         * 
         * @param rawResponse The raw response text from the AI
         * @return ValidatedAiResponse with all fields populated (using defaults if necessary)
         */
        fun validateAiResponse(rawResponse: String?): ValidatedAiResponse {
            if (rawResponse.isNullOrEmpty()) {
                return ValidatedAiResponse(
                    name = DEFAULT_NAME,
                    script = ERROR_SCRIPT,
                    explanation = JSON_ERROR_EXPLANATION,
                    isValid = false
                )
            }

            // Extract JSON from the response using JsonUtils
            val jsonText = JsonUtils.extractFirstJsonObject(rawResponse)
            
            if (jsonText == null) {
                return ValidatedAiResponse(
                    name = DEFAULT_NAME,
                    script = ERROR_SCRIPT,
                    explanation = JSON_ERROR_EXPLANATION,
                    isValid = false
                )
            }

            return try {
                val json = JSONObject(jsonText)
                
                // Extract fields with fallback to defaults
                val name = json.optString("name", "").let { 
                    if (it.isBlank()) DEFAULT_NAME else it 
                }
                
                val script = json.optString("script", "").let { 
                    if (it.isBlank()) DEFAULT_SCRIPT else it 
                }
                
                val explanation = json.optString("explanation", "").let { 
                    if (it.isBlank()) DEFAULT_EXPLANATION else it 
                }

                // Response is valid if we successfully parsed JSON, regardless of whether we used defaults
                ValidatedAiResponse(
                    name = name,
                    script = script,
                    explanation = explanation,
                    isValid = true
                )
                
            } catch (e: Exception) {
                // Debug: Print the exception
                println("DEBUG: JSON parsing exception: ${e.message}")
                println("DEBUG: JSON text was: $jsonText")
                ValidatedAiResponse(
                    name = DEFAULT_NAME,
                    script = ERROR_SCRIPT,
                    explanation = JSON_ERROR_EXPLANATION,
                    isValid = false
                )
            }
        }

        /**
         * Validates individual field presence and content.
         * 
         * @param json The JSONObject to validate
         * @param fieldName The field name to check
         * @return true if field exists and has non-empty content
         */
        fun isFieldValid(json: JSONObject, fieldName: String): Boolean {
            return json.has(fieldName) && json.optString(fieldName, "").isNotBlank()
        }

        /**
         * Checks if a response contains all required fields with valid content.
         * 
         * @param rawResponse The raw response text
         * @return true if all required fields are present and valid
         */
        fun hasAllRequiredFields(rawResponse: String?): Boolean {
            val jsonText = JsonUtils.extractFirstJsonObject(rawResponse) ?: return false
            
            return try {
                val json = JSONObject(jsonText)
                isFieldValid(json, "name") && 
                isFieldValid(json, "script") && 
                isFieldValid(json, "explanation")
            } catch (e: Exception) {
                false
            }
        }
    }

    private lateinit var validator: AiResponseValidator

    @BeforeEach
    fun setUp() {
        validator = AiResponseValidator()
    }

    // ========== Valid Response Tests (Requirement 2.1) ==========

    @Nested
    @DisplayName("Valid AI Response Validation (Requirement 2.1)")
    inner class ValidResponseTests {

        @Test
        @DisplayName("DEBUG: Test JSONObject with detailed debugging")
        fun testJsonObjectDirectly() {
            val jsonText = """{"name":"Test Tool","script":"console.log('test');","explanation":"A test script"}"""
            
            try {
                val json = JSONObject(jsonText)
                println("DEBUG: JSONObject created successfully")
                println("DEBUG: JSON toString: ${json.toString()}")
                println("DEBUG: JSON keys: ${json.keys().asSequence().toList()}")
                println("DEBUG: has name key: ${json.has("name")}")
                println("DEBUG: name = '${json.optString("name")}'")
                println("DEBUG: name (getString) = '${json.getString("name")}'")
                println("DEBUG: script = '${json.optString("script")}'")
                println("DEBUG: explanation = '${json.optString("explanation")}'")
                
                // Test the actual values
                assertEquals("Test Tool", json.getString("name"), "Name should match")
                assertTrue(true, "JSONObject should work")
            } catch (e: Exception) {
                println("DEBUG: JSONObject exception: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
                fail("JSONObject should not throw exception")
            }
        }

        @Test
        @DisplayName("Should validate response with markdown formatting")
        fun validateMarkdownResponse() {
            val response = """
                Here's your tool:
                ```json
                {"name":"Markdown Tool","script":"alert('from markdown');","explanation":"Extracted from markdown"}
                ```
                Hope this helps!
            """.trimIndent()
            
            val result = validator.validateAiResponse(response)
            
            assertTrue(result.isValid, "Markdown response should be valid")
            assertEquals("Markdown Tool", result.name, "Should extract name from markdown")
            assertEquals("alert('from markdown');", result.script, "Should extract script from markdown")
            assertEquals("Extracted from markdown", result.explanation, "Should extract explanation from markdown")
        }

        @Test
        @DisplayName("Should validate response with complex nested JSON")
        fun validateComplexResponse() {
            val response = """{"name":"Complex Tool","script":"const config = {\"enabled\": true}; console.log(config);","explanation":"Uses nested JSON structures"}"""
            val result = validator.validateAiResponse(response)
            
            assertTrue(result.isValid, "Complex response should be valid")
            assertEquals("Complex Tool", result.name)
            assertTrue(result.script.contains("const config"), "Should preserve complex script")
            assertEquals("Uses nested JSON structures", result.explanation)
        }

        @Test
        @DisplayName("Should validate response with Unicode characters")
        fun validateUnicodeResponse() {
            val response = """{"name":"Unicode Tool สวัสดี 🌟","script":"alert('สวัสดีครับ!');","explanation":"รองรับภาษาไทยและ Unicode"}"""
            val result = validator.validateAiResponse(response)
            
            assertTrue(result.isValid, "Unicode response should be valid")
            assertEquals("Unicode Tool สวัสดี 🌟", result.name, "Should preserve Unicode in name")
            assertTrue(result.script.contains("สวัสดีครับ"), "Should preserve Unicode in script")
            assertEquals("รองรับภาษาไทยและ Unicode", result.explanation, "Should preserve Unicode in explanation")
        }

        @Test
        @DisplayName("Should validate hasAllRequiredFields for complete response")
        fun validateHasAllRequiredFields() {
            val completeResponse = """{"name":"Complete","script":"test();","explanation":"All fields present"}"""
            val incompleteResponse = """{"name":"Incomplete","script":"test();"}"""
            
            assertTrue(validator.hasAllRequiredFields(completeResponse), "Should detect all required fields")
            assertFalse(validator.hasAllRequiredFields(incompleteResponse), "Should detect missing fields")
        }
    }

    // ========== Missing Field Default Tests (Requirements 2.2, 2.3, 2.4) ==========

    @Nested
    @DisplayName("Missing Field Default Provision (Requirements 2.2, 2.3, 2.4)")
    inner class MissingFieldDefaultTests {

        @Test
        @DisplayName("Should provide default name when name field is missing (Requirement 2.2)")
        fun provideDefaultNameWhenMissing() {
            val response = """{"script":"console.log('test');","explanation":"Missing name field"}"""
            val result = validator.validateAiResponse(response)
            
            assertTrue(result.isValid, "Response should still be valid")
            assertEquals(AiResponseValidator.DEFAULT_NAME, result.name, "Should provide default name")
            assertEquals("console.log('test');", result.script, "Should preserve existing script")
            assertEquals("Missing name field", result.explanation, "Should preserve existing explanation")
        }

        @Test
        @DisplayName("Should provide default script when script field is missing (Requirement 2.3)")
        fun provideDefaultScriptWhenMissing() {
            val response = """{"name":"Test Tool","explanation":"Missing script field"}"""
            val result = validator.validateAiResponse(response)
            
            assertTrue(result.isValid, "Response should still be valid")
            assertEquals("Test Tool", result.name, "Should preserve existing name")
            assertEquals(AiResponseValidator.DEFAULT_SCRIPT, result.script, "Should provide default script")
            assertEquals("Missing script field", result.explanation, "Should preserve existing explanation")
        }

        @Test
        @DisplayName("Should provide default explanation when explanation field is missing (Requirement 2.4)")
        fun provideDefaultExplanationWhenMissing() {
            val response = """{"name":"Test Tool","script":"console.log('test');"}"""
            val result = validator.validateAiResponse(response)
            
            assertTrue(result.isValid, "Response should still be valid")
            assertEquals("Test Tool", result.name, "Should preserve existing name")
            assertEquals("console.log('test');", result.script, "Should preserve existing script")
            assertEquals(AiResponseValidator.DEFAULT_EXPLANATION, result.explanation, "Should provide default explanation")
        }

        @Test
        @DisplayName("Should provide all defaults when all fields are missing")
        fun provideAllDefaultsWhenAllMissing() {
            val response = """{}"""
            val result = validator.validateAiResponse(response)
            
            assertTrue(result.isValid, "Empty JSON should still be processable")
            assertEquals(AiResponseValidator.DEFAULT_NAME, result.name, "Should provide default name")
            assertEquals(AiResponseValidator.DEFAULT_SCRIPT, result.script, "Should provide default script")
            assertEquals(AiResponseValidator.DEFAULT_EXPLANATION, result.explanation, "Should provide default explanation")
        }

        @Test
        @DisplayName("Should provide defaults for multiple missing fields")
        fun provideDefaultsForMultipleMissingFields() {
            val testCases = listOf(
                // Missing name and script
                """{"explanation":"Only explanation present"}""" to Triple(AiResponseValidator.DEFAULT_NAME, AiResponseValidator.DEFAULT_SCRIPT, "Only explanation present"),
                // Missing name and explanation
                """{"script":"alert('test');"}""" to Triple(AiResponseValidator.DEFAULT_NAME, "alert('test');", AiResponseValidator.DEFAULT_EXPLANATION),
                // Missing script and explanation
                """{"name":"Only Name"}""" to Triple("Only Name", AiResponseValidator.DEFAULT_SCRIPT, AiResponseValidator.DEFAULT_EXPLANATION)
            )
            
            testCases.forEach { (response, expected) ->
                val result = validator.validateAiResponse(response)
                assertTrue(result.isValid, "Response should be valid: $response")
                assertEquals(expected.first, result.name, "Name should match expected for: $response")
                assertEquals(expected.second, result.script, "Script should match expected for: $response")
                assertEquals(expected.third, result.explanation, "Explanation should match expected for: $response")
            }
        }
    }

    // ========== Empty String Handling Tests (Requirement 2.5) ==========

    @Nested
    @DisplayName("Empty String Handling and Replacement (Requirement 2.5)")
    inner class EmptyStringHandlingTests {

        @Test
        @DisplayName("Should replace empty name string with default")
        fun replaceEmptyNameString() {
            val response = """{"name":"","script":"console.log('test');","explanation":"Empty name field"}"""
            val result = validator.validateAiResponse(response)
            
            assertTrue(result.isValid, "Response should be valid")
            assertEquals(AiResponseValidator.DEFAULT_NAME, result.name, "Should replace empty name with default")
            assertEquals("console.log('test');", result.script, "Should preserve non-empty script")
            assertEquals("Empty name field", result.explanation, "Should preserve non-empty explanation")
        }

        @Test
        @DisplayName("Should replace empty script string with default")
        fun replaceEmptyScriptString() {
            val response = """{"name":"Test Tool","script":"","explanation":"Empty script field"}"""
            val result = validator.validateAiResponse(response)
            
            assertTrue(result.isValid, "Response should be valid")
            assertEquals("Test Tool", result.name, "Should preserve non-empty name")
            assertEquals(AiResponseValidator.DEFAULT_SCRIPT, result.script, "Should replace empty script with default")
            assertEquals("Empty script field", result.explanation, "Should preserve non-empty explanation")
        }

        @Test
        @DisplayName("Should replace empty explanation string with default")
        fun replaceEmptyExplanationString() {
            val response = """{"name":"Test Tool","script":"console.log('test');","explanation":""}"""
            val result = validator.validateAiResponse(response)
            
            assertTrue(result.isValid, "Response should be valid")
            assertEquals("Test Tool", result.name, "Should preserve non-empty name")
            assertEquals("console.log('test');", result.script, "Should preserve non-empty script")
            assertEquals(AiResponseValidator.DEFAULT_EXPLANATION, result.explanation, "Should replace empty explanation with default")
        }

        @Test
        @DisplayName("Should replace all empty strings with defaults")
        fun replaceAllEmptyStrings() {
            val response = """{"name":"","script":"","explanation":""}"""
            val result = validator.validateAiResponse(response)
            
            assertTrue(result.isValid, "Response should be valid")
            assertEquals(AiResponseValidator.DEFAULT_NAME, result.name, "Should replace empty name")
            assertEquals(AiResponseValidator.DEFAULT_SCRIPT, result.script, "Should replace empty script")
            assertEquals(AiResponseValidator.DEFAULT_EXPLANATION, result.explanation, "Should replace empty explanation")
        }

        @Test
        @DisplayName("Should handle whitespace-only strings as empty")
        fun handleWhitespaceOnlyStrings() {
            val response = """{"name":"   ","script":"\t\n","explanation":"  \t  "}"""
            val result = validator.validateAiResponse(response)
            
            assertTrue(result.isValid, "Response should be valid")
            assertEquals(AiResponseValidator.DEFAULT_NAME, result.name, "Should treat whitespace-only name as empty")
            assertEquals(AiResponseValidator.DEFAULT_SCRIPT, result.script, "Should treat whitespace-only script as empty")
            assertEquals(AiResponseValidator.DEFAULT_EXPLANATION, result.explanation, "Should treat whitespace-only explanation as empty")
        }

        @Test
        @DisplayName("Should preserve strings with meaningful content")
        fun preserveMeaningfulContent() {
            val response = """{"name":" Valid Name ","script":"  console.log('test');  ","explanation":"  Valid explanation  "}"""
            val result = validator.validateAiResponse(response)
            
            assertTrue(result.isValid, "Response should be valid")
            assertEquals(" Valid Name ", result.name, "Should preserve name with surrounding spaces")
            assertEquals("  console.log('test');  ", result.script, "Should preserve script with surrounding spaces")
            assertEquals("  Valid explanation  ", result.explanation, "Should preserve explanation with surrounding spaces")
        }
    }

    // ========== Error Handling Tests ==========

    @Nested
    @DisplayName("Error Handling and Edge Cases")
    inner class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle null response gracefully")
        fun handleNullResponse() {
            val result = validator.validateAiResponse(null)
            
            assertFalse(result.isValid, "Null response should be invalid")
            assertEquals(AiResponseValidator.DEFAULT_NAME, result.name, "Should provide default name for null")
            assertEquals(AiResponseValidator.ERROR_SCRIPT, result.script, "Should provide error script for null")
            assertEquals(AiResponseValidator.JSON_ERROR_EXPLANATION, result.explanation, "Should provide error explanation for null")
        }

        @Test
        @DisplayName("Should handle empty response gracefully")
        fun handleEmptyResponse() {
            val result = validator.validateAiResponse("")
            
            assertFalse(result.isValid, "Empty response should be invalid")
            assertEquals(AiResponseValidator.DEFAULT_NAME, result.name, "Should provide default name for empty")
            assertEquals(AiResponseValidator.ERROR_SCRIPT, result.script, "Should provide error script for empty")
            assertEquals(AiResponseValidator.JSON_ERROR_EXPLANATION, result.explanation, "Should provide error explanation for empty")
        }

        @Test
        @DisplayName("Should handle response with no JSON content")
        fun handleNoJsonResponse() {
            val response = "I'm sorry, I couldn't generate a tool for that request. Please try a different prompt."
            val result = validator.validateAiResponse(response)
            
            assertFalse(result.isValid, "No JSON response should be invalid")
            assertEquals(AiResponseValidator.DEFAULT_NAME, result.name, "Should provide default name for no JSON")
            assertEquals(AiResponseValidator.ERROR_SCRIPT, result.script, "Should provide error script for no JSON")
            assertEquals(AiResponseValidator.JSON_ERROR_EXPLANATION, result.explanation, "Should provide error explanation for no JSON")
        }

        @Test
        @DisplayName("Should handle malformed JSON gracefully")
        fun handleMalformedJson() {
            val malformedResponses = listOf(
                """{"name": "Test", "script": "alert('test'", "explanation": "Missing quote and brace""",
                """{"name": "Test" "script": "alert('test');", "explanation": "Missing comma"}""",
                """{name: "Test", "script": "alert('test');", "explanation": "Unquoted key"}""",
                """{"name": "Test", "script": "alert('test');", "explanation": }"""
            )
            
            malformedResponses.forEach { response ->
                val result = validator.validateAiResponse(response)
                assertFalse(result.isValid, "Malformed JSON should be invalid: $response")
                assertEquals(AiResponseValidator.DEFAULT_NAME, result.name, "Should provide default name for malformed JSON")
                assertEquals(AiResponseValidator.ERROR_SCRIPT, result.script, "Should provide error script for malformed JSON")
                assertEquals(AiResponseValidator.JSON_ERROR_EXPLANATION, result.explanation, "Should provide error explanation for malformed JSON")
            }
        }

        @Test
        @DisplayName("Should handle response with multiple JSON objects by taking first")
        fun handleMultipleJsonObjects() {
            val response = """
                First: {"name":"First Tool","script":"console.log('1');","explanation":"First option"}
                Second: {"name":"Second Tool","script":"console.log('2');","explanation":"Second option"}
            """.trimIndent()
            
            val result = validator.validateAiResponse(response)
            
            assertTrue(result.isValid, "Should successfully parse first JSON object")
            assertEquals("First Tool", result.name, "Should extract from first JSON object")
            assertEquals("console.log('1');", result.script, "Should extract script from first JSON object")
            assertEquals("First option", result.explanation, "Should extract explanation from first JSON object")
        }
    }

    // ========== Integration Tests with JsonUtils ==========

    @Nested
    @DisplayName("Integration with JsonUtils")
    inner class JsonUtilsIntegrationTests {

        @Test
        @DisplayName("Should work correctly with JsonUtils extraction")
        fun integrateWithJsonUtilsExtraction() {
            val response = """
                AI Response: Here's your tool!
                
                ```json
                {"name":"Integration Test","script":"console.log('integrated');","explanation":"Tests JsonUtils integration"}
                ```
                
                This should work perfectly.
            """.trimIndent()
            
            // Test JsonUtils extraction directly
            val extractedJson = JsonUtils.extractFirstJsonObject(response)
            assertNotNull(extractedJson, "JsonUtils should extract JSON")
            
            // Test validator integration
            val result = validator.validateAiResponse(response)
            assertTrue(result.isValid, "Validator should work with JsonUtils extraction")
            assertEquals("Integration Test", result.name)
            assertEquals("console.log('integrated');", result.script)
            assertEquals("Tests JsonUtils integration", result.explanation)
        }

        @Test
        @DisplayName("Should handle JsonUtils extraction failures gracefully")
        fun handleJsonUtilsExtractionFailures() {
            val responses = listOf(
                "No JSON content here at all",
                "Unclosed brace: {\"name\":\"test\"",
                "Just some { random } braces without valid JSON"
            )
            
            responses.forEach { response ->
                val extractedJson = JsonUtils.extractFirstJsonObject(response)
                val result = validator.validateAiResponse(response)
                
                if (extractedJson == null) {
                    assertFalse(result.isValid, "Should be invalid when JsonUtils returns null: $response")
                    assertEquals(AiResponseValidator.ERROR_SCRIPT, result.script, "Should use error script when extraction fails")
                }
            }
        }
    }

    // ========== Field Validation Helper Tests ==========

    @Nested
    @DisplayName("Field Validation Helper Methods")
    inner class FieldValidationHelperTests {

        @Test
        @DisplayName("Should correctly validate individual fields")
        fun validateIndividualFields() {
            val json = JSONObject("""{"name":"Valid","script":"","explanation":"Valid explanation"}""")
            
            assertTrue(validator.isFieldValid(json, "name"), "Should validate non-empty name field")
            assertFalse(validator.isFieldValid(json, "script"), "Should invalidate empty script field")
            assertTrue(validator.isFieldValid(json, "explanation"), "Should validate non-empty explanation field")
            assertFalse(validator.isFieldValid(json, "nonexistent"), "Should invalidate non-existent field")
        }

        @Test
        @DisplayName("Should handle field validation edge cases")
        fun validateFieldEdgeCases() {
            val testCases = listOf(
                """{"name":null,"script":"valid","explanation":"valid"}""" to listOf(false, true, true),
                """{"name":"   ","script":"valid","explanation":"valid"}""" to listOf(false, true, true), // whitespace-only
                """{"name":"valid","script":123,"explanation":"valid"}""" to listOf(true, true, true), // number converted to string
                """{"name":"valid","script":true,"explanation":"valid"}""" to listOf(true, true, true) // boolean converted to string
            )
            
            testCases.forEach { (jsonString, expectedValidations) ->
                val json = JSONObject(jsonString)
                val fields = listOf("name", "script", "explanation")
                
                fields.forEachIndexed { index, field ->
                    val isValid = validator.isFieldValid(json, field)
                    assertEquals(expectedValidations[index], isValid, 
                        "Field '$field' validation should match expected for: $jsonString")
                }
            }
        }
    }

    // ========== Mock Response Integration Tests ==========

    @Nested
    @DisplayName("Mock Response Integration Tests")
    inner class MockResponseIntegrationTests {

        @Test
        @DisplayName("Should validate MockResponse success responses")
        fun validateMockResponseSuccess() {
            val mockResponse = MockResponse.createSuccessResponse(
                name = "Mock Tool",
                script = "console.log('mock');",
                explanation = "Generated by mock"
            )
            
            val result = validator.validateAiResponse(mockResponse.text)
            
            assertTrue(result.isValid, "Mock success response should be valid")
            assertEquals("Mock Tool", result.name)
            assertEquals("console.log('mock');", result.script)
            assertEquals("Generated by mock", result.explanation)
        }

        @Test
        @DisplayName("Should handle MockResponse incomplete responses")
        fun validateMockResponseIncomplete() {
            val mockResponse = MockResponse.createIncompleteResponse(
                includeName = true,
                includeScript = false,
                includeExplanation = true
            )
            
            val result = validator.validateAiResponse(mockResponse.text)
            
            assertTrue(result.isValid, "Mock incomplete response should still be valid")
            assertEquals("Incomplete Tool", result.name, "Should preserve included name")
            assertEquals(AiResponseValidator.DEFAULT_SCRIPT, result.script, "Should provide default for missing script")
            assertEquals("Missing some fields", result.explanation, "Should preserve included explanation")
        }

        @Test
        @DisplayName("Should handle MockResponse empty fields")
        fun validateMockResponseEmptyFields() {
            val mockResponse = MockResponse.createEmptyFieldsResponse()
            
            val result = validator.validateAiResponse(mockResponse.text)
            
            assertTrue(result.isValid, "Mock empty fields response should be valid")
            assertEquals(AiResponseValidator.DEFAULT_NAME, result.name, "Should replace empty name")
            assertEquals(AiResponseValidator.DEFAULT_SCRIPT, result.script, "Should replace empty script")
            assertEquals(AiResponseValidator.DEFAULT_EXPLANATION, result.explanation, "Should replace empty explanation")
        }

        @Test
        @DisplayName("Should handle MockResponse malformed JSON")
        fun validateMockResponseMalformed() {
            val mockResponse = MockResponse.createMalformedJsonResponse()
            
            val result = validator.validateAiResponse(mockResponse.text)
            
            assertFalse(result.isValid, "Mock malformed response should be invalid")
            assertEquals(AiResponseValidator.DEFAULT_NAME, result.name, "Should provide default name for malformed")
            assertEquals(AiResponseValidator.ERROR_SCRIPT, result.script, "Should provide error script for malformed")
            assertEquals(AiResponseValidator.JSON_ERROR_EXPLANATION, result.explanation, "Should provide error explanation for malformed")
        }
    }
}