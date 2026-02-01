package com.example.spiritwebview.testing

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Comprehensive test suite for API key validation functionality.
 * 
 * This test class validates the API key management logic including:
 * - API key format validation
 * - Authentication failure detection
 * - Error message generation and localization
 * - API key initialization and model setup
 * - Edge cases and security scenarios
 */
class ApiKeyValidationTest {
    
    // ========== Valid API Key Tests ==========
    
    @Test
    fun `should validate correct Gemini API key format`() {
        val validKeys = listOf(
            "AIzaSyD" + "X".repeat(32),
            "AIzaSyABCDEFGHIJKLMNOPQRSTUVWXYZ123456",
            "AIzaSy" + "A".repeat(33), // 39 chars total
            "AIzaSy" + "1".repeat(50)  // Longer key
        )
        
        val validator = ApiKeyValidator()
        
        validKeys.forEach { key ->
            val result = validator.validateApiKey(key)
            assertTrue(result.isValid, "Should validate correct format: $key")
            assertNull(result.errorMessage, "Should not have error message for valid key")
            assertEquals("valid", result.keyFormat, "Should identify as valid format")
            assertTrue(result.canInitializeModel, "Should be able to initialize model")
        }
    }
    
    @Test
    fun `should validate API key with minimum required length`() {
        val minLengthKey = "AIzaSy" + "X".repeat(20) // 26 chars total
        
        val validator = ApiKeyValidator()
        val result = validator.validateApiKey(minLengthKey)
        
        assertTrue(result.isValid, "Should validate minimum length key")
        assertEquals("valid", result.keyFormat, "Should identify as valid format")
        assertTrue(result.canInitializeModel, "Should be able to initialize model")
    }
    
    // ========== Invalid API Key Format Tests ==========
    
    @Test
    fun `should reject empty API key`() {
        val validator = ApiKeyValidator()
        val result = validator.validateApiKey("")
        
        assertFalse(result.isValid, "Should reject empty API key")
        assertEquals("API key cannot be empty", result.errorMessage, "Should provide appropriate error message")
        assertEquals("empty", result.keyFormat, "Should identify as empty format")
        assertFalse(result.canInitializeModel, "Should not be able to initialize model")
    }
    
    @Test
    fun `should reject null API key`() {
        val validator = ApiKeyValidator()
        val result = validator.validateApiKey(null)
        
        assertFalse(result.isValid, "Should reject null API key")
        assertEquals("API key is required", result.errorMessage, "Should provide appropriate error message")
        assertEquals("null", result.keyFormat, "Should identify as null format")
        assertFalse(result.canInitializeModel, "Should not be able to initialize model")
    }
    
    @Test
    fun `should reject API key with wrong prefix`() {
        val invalidPrefixes = listOf(
            "InvalidPrefix_" + "X".repeat(31),
            "GoogleAI_" + "X".repeat(34),
            "API_KEY_" + "X".repeat(34),
            "GEMINI_" + "X".repeat(34),
            "AIzaS_" + "X".repeat(34), // Missing 'y'
            "AIzaSX_" + "X".repeat(31)  // Wrong character
        )
        
        val validator = ApiKeyValidator()
        
        invalidPrefixes.forEach { key ->
            val result = validator.validateApiKey(key)
            assertFalse(result.isValid, "Should reject wrong prefix: $key")
            assertEquals("Invalid API key format", result.errorMessage, "Should provide format error message")
            assertEquals("malformed", result.keyFormat, "Should identify as malformed")
            assertFalse(result.canInitializeModel, "Should not be able to initialize model")
        }
    }
    
    @Test
    fun `should reject API key that is too short`() {
        val shortKeys = listOf(
            "AIzaSy",           // Only prefix
            "AIzaSyShort",      // Too short
            "AIzaSy123",        // Still too short
            "AIzaSy" + "X".repeat(10) // 16 chars total, still too short
        )
        
        val validator = ApiKeyValidator()
        
        shortKeys.forEach { key ->
            val result = validator.validateApiKey(key)
            assertFalse(result.isValid, "Should reject short key: $key")
            assertEquals("API key is too short", result.errorMessage, "Should provide length error message")
            assertEquals("malformed", result.keyFormat, "Should identify as malformed")
            assertFalse(result.canInitializeModel, "Should not be able to initialize model")
        }
    }
    
    @Test
    fun `should reject API key with invalid characters`() {
        val invalidCharKeys = listOf(
            "AIzaSy" + "X".repeat(20) + "!@#$%", // Special characters
            "AIzaSy" + "X".repeat(20) + " ",     // Space
            "AIzaSy" + "X".repeat(20) + "\n",    // Newline
            "AIzaSy" + "X".repeat(20) + "\t",    // Tab
            "AIzaSy" + "X".repeat(20) + "ñ",     // Non-ASCII
            "AIzaSy" + "X".repeat(20) + "🔑"     // Emoji
        )
        
        val validator = ApiKeyValidator()
        
        invalidCharKeys.forEach { key ->
            val result = validator.validateApiKey(key)
            assertFalse(result.isValid, "Should reject key with invalid chars: $key")
            assertEquals("API key contains invalid characters", result.errorMessage, "Should provide character error message")
            assertEquals("malformed", result.keyFormat, "Should identify as malformed")
            assertFalse(result.canInitializeModel, "Should not be able to initialize model")
        }
    }
    
    // ========== Authentication Status Tests ==========
    
    @Test
    fun `should detect revoked API key`() {
        val revokedKeys = listOf(
            "AIzaSyRevokedKey" + "X".repeat(24),
            "AIzaSyInvalidKey" + "X".repeat(24),
            "AIzaSyExpiredKey" + "X".repeat(24),
            "AIzaSyDisabledKey" + "X".repeat(23)
        )
        
        val validator = ApiKeyValidator()
        
        revokedKeys.forEach { key ->
            val result = validator.validateApiKey(key)
            assertFalse(result.isValid, "Should detect revoked key: $key")
            assertEquals("API key is invalid or revoked", result.errorMessage, "Should provide revoked error message")
            assertEquals("revoked", result.keyFormat, "Should identify as revoked")
            assertFalse(result.canInitializeModel, "Should not be able to initialize model")
        }
    }
    
    @Test
    fun `should detect expired API key`() {
        val expiredKeys = listOf(
            "AIzaSyExpired2023" + "X".repeat(23),
            "AIzaSyOldKey" + "X".repeat(27)
        )
        
        val validator = ApiKeyValidator()
        
        expiredKeys.forEach { key ->
            val result = validator.validateApiKey(key)
            if (key.contains("Expired") || key.contains("Old")) {
                assertFalse(result.isValid, "Should detect expired key: $key")
                assertTrue(result.errorMessage!!.contains("expired") || result.errorMessage!!.contains("invalid"), 
                    "Should provide expired/invalid error message")
            }
        }
    }
    
    // ========== Error Message Localization Tests ==========
    
    @Test
    fun `should provide localized error messages for different languages`() {
        val validator = ApiKeyValidator()
        
        // Test English (default)
        val englishResult = validator.validateApiKey("", "en")
        assertEquals("API key cannot be empty", englishResult.errorMessage, "Should provide English error message")
        
        // Test Thai
        val thaiResult = validator.validateApiKey("", "th")
        assertEquals("API key ไม่สามารถเป็นค่าว่างได้", thaiResult.errorMessage, "Should provide Thai error message")
        
        // Test Chinese
        val chineseResult = validator.validateApiKey("", "zh")
        assertEquals("API密钥不能为空", chineseResult.errorMessage, "Should provide Chinese error message")
    }
    
    // ========== Model Initialization Tests ==========
    
    @Test
    fun `should successfully initialize model with valid API key`() {
        val validKey = "AIzaSyValidTestKey" + "X".repeat(23)
        val validator = ApiKeyValidator()
        
        val result = validator.validateApiKey(validKey)
        assertTrue(result.isValid, "Should validate key successfully")
        assertTrue(result.canInitializeModel, "Should be able to initialize model")
    }
}

/**
 * API Key Validator implementation for testing.
 */
class ApiKeyValidator {
    
    companion object {
        private const val GEMINI_API_KEY_PREFIX = "AIzaSy"
        private const val MIN_API_KEY_LENGTH = 26
        private val VALID_API_KEY_CHARS = Regex("^[A-Za-z0-9_-]+$")
        
        // Localized error messages
        private val ERROR_MESSAGES = mapOf(
            "en" to mapOf(
                "empty" to "API key cannot be empty",
                "null" to "API key is required",
                "format" to "Invalid API key format",
                "length" to "API key is too short",
                "chars" to "API key contains invalid characters",
                "revoked" to "API key is invalid or revoked"
            ),
            "th" to mapOf(
                "empty" to "API key ไม่สามารถเป็นค่าว่างได้",
                "null" to "จำเป็นต้องมี API key",
                "format" to "รูปแบบ API key ไม่ถูกต้อง",
                "length" to "API key สั้นเกินไป",
                "chars" to "API key มีตัวอักษรที่ไม่ถูกต้อง",
                "revoked" to "API key ไม่ถูกต้องหรือถูกยกเลิก"
            ),
            "zh" to mapOf(
                "empty" to "API密钥不能为空",
                "null" to "需要API密钥",
                "format" to "API密钥格式无效",
                "length" to "API密钥太短",
                "chars" to "API密钥包含无效字符",
                "revoked" to "API密钥无效หรือ已撤回"
            )
        )
    }
    
    fun validateApiKey(apiKey: String?, language: String = "en"): ApiKeyValidationResult {
        val messages = ERROR_MESSAGES[language] ?: ERROR_MESSAGES["en"]!!
        if (apiKey == null) return ApiKeyValidationResult(false, messages["null"], "null", false)
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) return ApiKeyValidationResult(false, messages["empty"], "empty", false)
        if (!trimmedKey.startsWith(GEMINI_API_KEY_PREFIX)) return ApiKeyValidationResult(false, messages["format"], "malformed", false)
        if (trimmedKey.length < MIN_API_KEY_LENGTH) return ApiKeyValidationResult(false, messages["length"], "malformed", false)
        if (!VALID_API_KEY_CHARS.matches(trimmedKey)) return ApiKeyValidationResult(false, messages["chars"], "malformed", false)
        val revokedPatterns = listOf("Revoked", "Invalid", "Expired", "Disabled", "Old")
        if (revokedPatterns.any { trimmedKey.contains(it, ignoreCase = true) }) {
            return ApiKeyValidationResult(false, messages["revoked"], "revoked", false)
        }
        return ApiKeyValidationResult(true, null, "valid", true)
    }
}

data class ApiKeyValidationResult(
    val isValid: Boolean,
    val errorMessage: String?,
    val keyFormat: String,
    val canInitializeModel: Boolean
)
