package com.example.spiritwebview.testing

import android.content.SharedPreferences
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Interface for AI generation functionality that can be mocked for testing.
 * 
 * This interface abstracts the GenerativeModel functionality to allow for
 * comprehensive testing of AI-related components without depending on the
 * actual Gemini API.
 */
interface GenerativeModelInterface {
    suspend fun generateContent(prompt: String): MockGenerateContentResponse
}

/**
 * Simple data class that mimics GenerateContentResponse for testing purposes.
 * 
 * This provides the essential properties needed for testing AI response handling
 * without the complexity of the actual Gemini API response structure.
 */
data class MockGenerateContentResponse(
    val text: String?,
    val candidates: List<String> = emptyList(),
    val promptFeedback: String? = null,
    val usageMetadata: String? = null
)

/**
 * Mock implementation of GenerativeModelInterface for comprehensive AI testing.
 * 
 * This mock provides configurable response simulation for testing all aspects
 * of AI functionality including success scenarios, error conditions, and edge cases.
 * 
 * Features:
 * - Configurable response simulation with realistic delays
 * - Network error simulation (timeouts, connectivity issues, SSL errors)
 * - API authentication error simulation
 * - Safety filter violation simulation
 * - Rate limiting simulation
 * - Malformed response simulation
 * - Custom response patterns for testing specific scenarios
 * 
 * Requirements Coverage:
 * - 3.1, 3.2, 3.3: API key management and validation
 * - 4.1-4.6: Network error handling scenarios
 * - 5.1-5.5: Safety filter and content restrictions
 * - 1.1-1.6: JSON parsing with various response formats
 * - 2.1-2.6: Response validation and field handling
 */
class MockGenerativeModel(
    private val configuration: MockApiConfiguration = MockApiConfiguration()
) : GenerativeModelInterface {

    /**
     * Generate content with configurable mock responses and error simulation.
     * 
     * @param prompt The input prompt (used to determine response type)
     * @return MockGenerateContentResponse with simulated content or throws configured exception
     */
    override suspend fun generateContent(prompt: String): MockGenerateContentResponse {
        // Simulate network delay
        delay(configuration.responseDelay)
        
        // Check for network simulation
        if (configuration.simulateNetworkIssues) {
            when (configuration.networkErrorType) {
                NetworkErrorType.TIMEOUT -> throw SocketTimeoutException("Connection timed out")
                NetworkErrorType.DNS_FAILURE -> throw UnknownHostException("Unable to resolve host")
                NetworkErrorType.SSL_ERROR -> throw SSLException("SSL handshake failed")
                NetworkErrorType.CONNECTION_REFUSED -> throw IOException("Connection refused")
                NetworkErrorType.NONE -> { /* Continue normally */ }
            }
        }
        
        // Check for API key errors
        if (configuration.simulateApiKeyError) {
            throw IllegalArgumentException("API key is invalid or expired")
        }
        
        // Check for rate limiting
        if (configuration.simulateRateLimit) {
            throw IOException("Rate limit exceeded. Please try again later.")
        }
        
        // Check for safety filter violations
        if (configuration.simulateSafetyViolation) {
            throw IllegalArgumentException("Content blocked by safety filters")
        }
        
        // Get response based on prompt or use default
        val mockResponse = configuration.responses[prompt] 
            ?: configuration.responses["default"]
            ?: MockResponse.createSuccessResponse()
        
        // Handle configured response
        return when {
            mockResponse.shouldThrowException -> {
                when (mockResponse.exceptionType) {
                    ExceptionType.NETWORK -> throw IOException("Network error occurred")
                    ExceptionType.SAFETY -> throw IllegalArgumentException("Content blocked by safety filters")
                    ExceptionType.API_KEY -> throw IllegalArgumentException("Invalid API key")
                    ExceptionType.TIMEOUT -> throw SocketTimeoutException("Request timeout")
                    ExceptionType.RATE_LIMIT -> throw IOException("Rate limit exceeded")
                    ExceptionType.UNKNOWN -> throw RuntimeException("Unknown error occurred")
                }
            }
            else -> {
                // Create mock response with the configured text
                MockGenerateContentResponse(text = mockResponse.text ?: "")
            }
        }
    }
}

/**
 * Configuration class for MockGenerativeModel behavior.
 * 
 * Allows comprehensive configuration of mock responses, error scenarios,
 * and timing behavior for thorough testing of AI functionality.
 */
data class MockApiConfiguration(
    /**
     * Map of prompts to their corresponding mock responses.
     * Use "default" key for fallback response.
     */
    val responses: Map<String, MockResponse> = mapOf(
        "default" to MockResponse.createSuccessResponse()
    ),
    
    /**
     * Delay in milliseconds before returning response (simulates network latency).
     */
    val responseDelay: Long = 100L,
    
    /**
     * Whether to simulate network connectivity issues.
     */
    val simulateNetworkIssues: Boolean = false,
    
    /**
     * Type of network error to simulate when simulateNetworkIssues is true.
     */
    val networkErrorType: NetworkErrorType = NetworkErrorType.NONE,
    
    /**
     * Whether to simulate API key authentication errors.
     */
    val simulateApiKeyError: Boolean = false,
    
    /**
     * Whether to simulate rate limiting errors.
     */
    val simulateRateLimit: Boolean = false,
    
    /**
     * Whether to simulate safety filter violations.
     */
    val simulateSafetyViolation: Boolean = false
)

/**
 * Mock response data for AI generation.
 * 
 * Represents different types of responses that can be returned by the mock,
 * including successful responses with valid JSON and various error scenarios.
 */
data class MockResponse(
    /**
     * The response text content. Can contain JSON, markdown, or plain text.
     */
    val text: String?,
    
    /**
     * Whether this response should throw an exception instead of returning content.
     */
    val shouldThrowException: Boolean = false,
    
    /**
     * Type of exception to throw when shouldThrowException is true.
     */
    val exceptionType: ExceptionType = ExceptionType.NETWORK
) {
    companion object {
        /**
         * Create a successful response with valid JSON for AI tool generation.
         */
        fun createSuccessResponse(
            name: String = "Generated Tool",
            script: String = "console.log('Generated script');",
            explanation: String = "This is a generated tool for testing purposes."
        ): MockResponse {
            val json = """
                {
                    "name": "$name",
                    "script": "$script",
                    "explanation": "$explanation"
                }
            """.trimIndent()
            
            return MockResponse(text = json)
        }
        
        /**
         * Create a response with JSON wrapped in markdown formatting.
         */
        fun createMarkdownResponse(
            name: String = "Markdown Tool",
            script: String = "alert('From markdown');",
            explanation: String = "Tool extracted from markdown."
        ): MockResponse {
            val json = """{"name": "$name", "script": "$script", "explanation": "$explanation"}"""
            val markdown = """
                Here's your JavaScript tool:
                
                ```json
                $json
                ```
                
                This tool should work perfectly for your needs!
            """.trimIndent()
            
            return MockResponse(text = markdown)
        }
        
        /**
         * Create a response with missing fields to test validation.
         */
        fun createIncompleteResponse(
            includeName: Boolean = true,
            includeScript: Boolean = true,
            includeExplanation: Boolean = true
        ): MockResponse {
            val jsonParts = mutableListOf<String>()
            
            if (includeName) jsonParts.add("\"name\": \"Incomplete Tool\"")
            if (includeScript) jsonParts.add("\"script\": \"console.log('incomplete');\"")
            if (includeExplanation) jsonParts.add("\"explanation\": \"Missing some fields\"")
            
            val json = "{${jsonParts.joinToString(", ")}}"
            return MockResponse(text = json)
        }
        
        /**
         * Create a response with empty string values to test default handling.
         */
        fun createEmptyFieldsResponse(): MockResponse {
            val json = """{"name": "", "script": "", "explanation": ""}"""
            return MockResponse(text = json)
        }
        
        /**
         * Create a response with malformed JSON to test error handling.
         */
        fun createMalformedJsonResponse(): MockResponse {
            val malformedJson = """{"name": "Malformed", "script": "alert('test'", "explanation": "Missing closing quote and brace"""
            return MockResponse(text = malformedJson)
        }
        
        /**
         * Create a response with no JSON content.
         */
        fun createNoJsonResponse(): MockResponse {
            val text = "I'm sorry, I couldn't generate a tool for that request. Please try a different prompt."
            return MockResponse(text = text)
        }
        
        /**
         * Create a response with multiple JSON objects to test first extraction.
         */
        fun createMultipleJsonResponse(): MockResponse {
            val text = """
                First option: {"name": "Option 1", "script": "console.log('1');", "explanation": "First choice"}
                
                Alternative: {"name": "Option 2", "script": "console.log('2');", "explanation": "Second choice"}
            """.trimIndent()
            return MockResponse(text = text)
        }
        
        /**
         * Create a response that simulates safety filter violation.
         */
        fun createSafetyViolationResponse(): MockResponse {
            return MockResponse(
                text = null,
                shouldThrowException = true,
                exceptionType = ExceptionType.SAFETY
            )
        }
        
        /**
         * Create a response that simulates network error.
         */
        fun createNetworkErrorResponse(): MockResponse {
            return MockResponse(
                text = null,
                shouldThrowException = true,
                exceptionType = ExceptionType.NETWORK
            )
        }
        
        /**
         * Create a response that simulates API key error.
         */
        fun createApiKeyErrorResponse(): MockResponse {
            return MockResponse(
                text = null,
                shouldThrowException = true,
                exceptionType = ExceptionType.API_KEY
            )
        }
        
        /**
         * Create a response with complex nested JSON to test parsing robustness.
         */
        fun createComplexJsonResponse(): MockResponse {
            val json = """
                {
                    "name": "Complex Tool",
                    "script": "const config = {\"enabled\": true, \"options\": [\"opt1\", \"opt2\"]}; console.log(config);",
                    "explanation": "A tool with complex nested structures and escaped quotes",
                    "metadata": {
                        "version": "1.0",
                        "author": {"name": "AI", "type": "generator"},
                        "tags": ["complex", "nested", "test"]
                    }
                }
            """.trimIndent()
            return MockResponse(text = json)
        }
        
        /**
         * Create a response with Unicode characters to test internationalization.
         */
        fun createUnicodeResponse(): MockResponse {
            val json = """
                {
                    "name": "Unicode Tool สวัสดี 🌟",
                    "script": "alert('สวัสดีครับ! 你好! こんにちは! مرحبا!');",
                    "explanation": "A tool that demonstrates Unicode support across multiple languages"
                }
            """.trimIndent()
            return MockResponse(text = json)
        }
    }
}

/**
 * Types of exceptions that can be simulated by the mock.
 */
enum class ExceptionType {
    NETWORK,        // Network connectivity issues
    SAFETY,         // Content blocked by safety filters
    API_KEY,        // Invalid or expired API key
    TIMEOUT,        // Request timeout
    RATE_LIMIT,     // API rate limit exceeded
    UNKNOWN         // Generic unknown error
}

/**
 * Types of network errors that can be simulated.
 */
enum class NetworkErrorType {
    NONE,               // No network error
    TIMEOUT,            // Connection timeout
    DNS_FAILURE,        // DNS resolution failure
    SSL_ERROR,          // SSL/TLS certificate error
    CONNECTION_REFUSED  // Connection refused by server
}

/**
 * Builder class for creating MockApiConfiguration instances with fluent API.
 * 
 * Provides a convenient way to configure mock behavior for different test scenarios.
 */
class MockApiConfigurationBuilder {
    private val responses = mutableMapOf<String, MockResponse>()
    private var responseDelay: Long = 100L
    private var simulateNetworkIssues: Boolean = false
    private var networkErrorType: NetworkErrorType = NetworkErrorType.NONE
    private var simulateApiKeyError: Boolean = false
    private var simulateRateLimit: Boolean = false
    private var simulateSafetyViolation: Boolean = false
    
    /**
     * Add a response for a specific prompt.
     */
    fun addResponse(prompt: String, response: MockResponse): MockApiConfigurationBuilder {
        responses[prompt] = response
        return this
    }
    
    /**
     * Set the default response for unmatched prompts.
     */
    fun setDefaultResponse(response: MockResponse): MockApiConfigurationBuilder {
        responses["default"] = response
        return this
    }
    
    /**
     * Set the response delay in milliseconds.
     */
    fun setResponseDelay(delay: Long): MockApiConfigurationBuilder {
        responseDelay = delay
        return this
    }
    
    /**
     * Enable network error simulation.
     */
    fun simulateNetworkError(errorType: NetworkErrorType = NetworkErrorType.TIMEOUT): MockApiConfigurationBuilder {
        simulateNetworkIssues = true
        networkErrorType = errorType
        return this
    }
    
    /**
     * Enable API key error simulation.
     */
    fun simulateApiKeyError(): MockApiConfigurationBuilder {
        simulateApiKeyError = true
        return this
    }
    
    /**
     * Enable rate limit error simulation.
     */
    fun simulateRateLimit(): MockApiConfigurationBuilder {
        simulateRateLimit = true
        return this
    }
    
    /**
     * Enable safety violation simulation.
     */
    fun simulateSafetyViolation(): MockApiConfigurationBuilder {
        simulateSafetyViolation = true
        return this
    }
    
    /**
     * Build the final MockApiConfiguration.
     */
    fun build(): MockApiConfiguration {
        return MockApiConfiguration(
            responses = responses.toMap(),
            responseDelay = responseDelay,
            simulateNetworkIssues = simulateNetworkIssues,
            networkErrorType = networkErrorType,
            simulateApiKeyError = simulateApiKeyError,
            simulateRateLimit = simulateRateLimit,
            simulateSafetyViolation = simulateSafetyViolation
        )
    }
}

/**
 * Mock implementation of SharedPreferences for comprehensive API key testing.
 * 
 * This mock provides configurable preference simulation for testing all aspects
 * of API key management including storage, retrieval, validation, corruption scenarios,
 * and recovery mechanisms.
 * 
 * Features:
 * - Configurable API key storage and retrieval
 * - Preference corruption simulation
 * - Editor operation simulation with success/failure scenarios
 * - Type safety validation for preference operations
 * - Realistic preference behavior simulation
 * - Support for all preference types used in the application
 * 
 * Requirements Coverage:
 * - 3.1: API key configuration and initialization
 * - 3.2: API key format validation
 * - 3.4: API key error handling and user feedback
 * - 3.5: API key validation success scenarios
 */
class MockSharedPreferences(
    private val configuration: MockPreferencesConfiguration = MockPreferencesConfiguration()
) : SharedPreferences {

    private val preferences = mutableMapOf<String, Any?>()
    private var isCorrupted = false

    init {
        // Initialize with configuration values
        preferences.putAll(configuration.initialValues)
        isCorrupted = configuration.simulateCorruption
    }

    override fun getAll(): MutableMap<String, *> {
        if (isCorrupted && configuration.corruptionAffectsGetAll) {
            throw RuntimeException("Preferences file corrupted")
        }
        return preferences.toMutableMap()
    }

    override fun getString(key: String?, defValue: String?): String? {
        if (isCorrupted && configuration.corruptionAffectsSpecificKeys.contains(key)) {
            return when (configuration.corruptionBehavior) {
                CorruptionBehavior.THROW_EXCEPTION -> throw RuntimeException("Preference corruption detected")
                CorruptionBehavior.RETURN_NULL -> null
                CorruptionBehavior.RETURN_DEFAULT -> defValue
                CorruptionBehavior.RETURN_CORRUPTED_VALUE -> "CORRUPTED_DATA_${System.currentTimeMillis()}"
            }
        }
        
        return preferences[key] as? String ?: defValue
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        if (isCorrupted && configuration.corruptionAffectsSpecificKeys.contains(key)) {
            return when (configuration.corruptionBehavior) {
                CorruptionBehavior.THROW_EXCEPTION -> throw RuntimeException("Preference corruption detected")
                CorruptionBehavior.RETURN_NULL -> null
                CorruptionBehavior.RETURN_DEFAULT -> defValues
                CorruptionBehavior.RETURN_CORRUPTED_VALUE -> mutableSetOf("CORRUPTED")
            }
        }
        
        @Suppress("UNCHECKED_CAST")
        return preferences[key] as? MutableSet<String> ?: defValues
    }

    override fun getInt(key: String?, defValue: Int): Int {
        if (isCorrupted && configuration.corruptionAffectsSpecificKeys.contains(key)) {
            return when (configuration.corruptionBehavior) {
                CorruptionBehavior.THROW_EXCEPTION -> throw RuntimeException("Preference corruption detected")
                CorruptionBehavior.RETURN_NULL, 
                CorruptionBehavior.RETURN_DEFAULT -> defValue
                CorruptionBehavior.RETURN_CORRUPTED_VALUE -> -999999
            }
        }
        
        return preferences[key] as? Int ?: defValue
    }

    override fun getLong(key: String?, defValue: Long): Long {
        if (isCorrupted && configuration.corruptionAffectsSpecificKeys.contains(key)) {
            return when (configuration.corruptionBehavior) {
                CorruptionBehavior.THROW_EXCEPTION -> throw RuntimeException("Preference corruption detected")
                CorruptionBehavior.RETURN_NULL,
                CorruptionBehavior.RETURN_DEFAULT -> defValue
                CorruptionBehavior.RETURN_CORRUPTED_VALUE -> -999999L
            }
        }
        
        return preferences[key] as? Long ?: defValue
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        if (isCorrupted && configuration.corruptionAffectsSpecificKeys.contains(key)) {
            return when (configuration.corruptionBehavior) {
                CorruptionBehavior.THROW_EXCEPTION -> throw RuntimeException("Preference corruption detected")
                CorruptionBehavior.RETURN_NULL,
                CorruptionBehavior.RETURN_DEFAULT -> defValue
                CorruptionBehavior.RETURN_CORRUPTED_VALUE -> Float.NaN
            }
        }
        
        return preferences[key] as? Float ?: defValue
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        if (isCorrupted && configuration.corruptionAffectsSpecificKeys.contains(key)) {
            return when (configuration.corruptionBehavior) {
                CorruptionBehavior.THROW_EXCEPTION -> throw RuntimeException("Preference corruption detected")
                CorruptionBehavior.RETURN_NULL,
                CorruptionBehavior.RETURN_DEFAULT -> defValue
                CorruptionBehavior.RETURN_CORRUPTED_VALUE -> !defValue // Return opposite
            }
        }
        
        return preferences[key] as? Boolean ?: defValue
    }

    override fun contains(key: String?): Boolean {
        if (isCorrupted && configuration.corruptionAffectsSpecificKeys.contains(key)) {
            return when (configuration.corruptionBehavior) {
                CorruptionBehavior.THROW_EXCEPTION -> throw RuntimeException("Preference corruption detected")
                CorruptionBehavior.RETURN_NULL -> false
                CorruptionBehavior.RETURN_DEFAULT -> false
                CorruptionBehavior.RETURN_CORRUPTED_VALUE -> true // Lie about containing the key
            }
        }
        
        return preferences.containsKey(key)
    }

    override fun edit(): SharedPreferences.Editor {
        return MockSharedPreferencesEditor(this, configuration.editorConfiguration)
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        // Mock implementation - could be enhanced to actually track listeners
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        // Mock implementation - could be enhanced to actually track listeners
    }

    /**
     * Internal method to update preferences (used by MockSharedPreferencesEditor).
     */
    internal fun updatePreferences(updates: Map<String, Any?>) {
        preferences.putAll(updates)
    }

    /**
     * Internal method to remove preferences (used by MockSharedPreferencesEditor).
     */
    internal fun removePreferences(keys: Set<String>) {
        keys.forEach { preferences.remove(it) }
    }

    /**
     * Internal method to clear all preferences (used by MockSharedPreferencesEditor).
     */
    internal fun clearPreferences() {
        preferences.clear()
    }

    /**
     * Test utility method to simulate corruption recovery.
     */
    fun simulateCorruptionRecovery() {
        isCorrupted = false
    }

    /**
     * Test utility method to trigger corruption.
     */
    fun simulateCorruption() {
        isCorrupted = true
    }

    /**
     * Test utility method to get current preference state for verification.
     */
    fun getCurrentPreferences(): Map<String, Any?> {
        return preferences.toMap()
    }
}

/**
 * Mock implementation of SharedPreferences.Editor for testing preference modifications.
 * 
 * Provides configurable behavior for testing various editor operation scenarios
 * including success, failure, and corruption scenarios.
 */
class MockSharedPreferencesEditor(
    private val mockPreferences: MockSharedPreferences,
    private val configuration: MockEditorConfiguration = MockEditorConfiguration()
) : SharedPreferences.Editor {

    private val pendingUpdates = mutableMapOf<String, Any?>()
    private val pendingRemovals = mutableSetOf<String>()
    private var shouldClear = false

    override fun putString(key: String?, value: String?): SharedPreferences.Editor {
        if (configuration.simulateWriteFailure) {
            // Don't actually store the value to simulate failure
            return this
        }
        
        key?.let { pendingUpdates[it] = value }
        return this
    }

    override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
        if (configuration.simulateWriteFailure) {
            return this
        }
        
        key?.let { pendingUpdates[it] = values }
        return this
    }

    override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
        if (configuration.simulateWriteFailure) {
            return this
        }
        
        key?.let { pendingUpdates[it] = value }
        return this
    }

    override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
        if (configuration.simulateWriteFailure) {
            return this
        }
        
        key?.let { pendingUpdates[it] = value }
        return this
    }

    override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
        if (configuration.simulateWriteFailure) {
            return this
        }
        
        key?.let { pendingUpdates[it] = value }
        return this
    }

    override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
        if (configuration.simulateWriteFailure) {
            return this
        }
        
        key?.let { pendingUpdates[it] = value }
        return this
    }

    override fun remove(key: String?): SharedPreferences.Editor {
        if (configuration.simulateWriteFailure) {
            return this
        }
        
        key?.let { pendingRemovals.add(it) }
        return this
    }

    override fun clear(): SharedPreferences.Editor {
        if (configuration.simulateWriteFailure) {
            return this
        }
        
        shouldClear = true
        return this
    }

    override fun commit(): Boolean {
        if (configuration.simulateCommitFailure) {
            return false
        }
        
        if (configuration.commitDelay > 0) {
            Thread.sleep(configuration.commitDelay)
        }
        
        applyChanges()
        return true
    }

    override fun apply() {
        if (configuration.simulateApplyFailure) {
            // Simulate apply failure by not actually applying changes
            return
        }
        
        // Apply is asynchronous, but for testing we'll make it synchronous
        if (configuration.applyDelay > 0) {
            Thread.sleep(configuration.applyDelay)
        }
        
        applyChanges()
    }

    private fun applyChanges() {
        if (shouldClear) {
            mockPreferences.clearPreferences()
        }
        
        mockPreferences.removePreferences(pendingRemovals)
        mockPreferences.updatePreferences(pendingUpdates)
        
        // Clear pending changes
        pendingUpdates.clear()
        pendingRemovals.clear()
        shouldClear = false
    }
}

/**
 * Configuration for MockSharedPreferences behavior.
 * 
 * Allows comprehensive configuration of preference behavior for testing
 * various scenarios including corruption, recovery, and error conditions.
 */
data class MockPreferencesConfiguration(
    /**
     * Initial values to populate the preferences with.
     */
    val initialValues: Map<String, Any?> = emptyMap(),
    
    /**
     * Whether to simulate preference file corruption.
     */
    val simulateCorruption: Boolean = false,
    
    /**
     * How corruption should behave when accessing preferences.
     */
    val corruptionBehavior: CorruptionBehavior = CorruptionBehavior.THROW_EXCEPTION,
    
    /**
     * Whether corruption affects the getAll() method.
     */
    val corruptionAffectsGetAll: Boolean = true,
    
    /**
     * Specific keys that are affected by corruption.
     */
    val corruptionAffectsSpecificKeys: Set<String> = emptySet(),
    
    /**
     * Configuration for the editor behavior.
     */
    val editorConfiguration: MockEditorConfiguration = MockEditorConfiguration()
)

/**
 * Configuration for MockSharedPreferencesEditor behavior.
 */
data class MockEditorConfiguration(
    /**
     * Whether to simulate write operation failures.
     */
    val simulateWriteFailure: Boolean = false,
    
    /**
     * Whether to simulate commit() method failures.
     */
    val simulateCommitFailure: Boolean = false,
    
    /**
     * Whether to simulate apply() method failures.
     */
    val simulateApplyFailure: Boolean = false,
    
    /**
     * Delay in milliseconds for commit() operations.
     */
    val commitDelay: Long = 0L,
    
    /**
     * Delay in milliseconds for apply() operations.
     */
    val applyDelay: Long = 0L
)

/**
 * Defines how preference corruption should behave.
 */
enum class CorruptionBehavior {
    /**
     * Throw a RuntimeException when accessing corrupted preferences.
     */
    THROW_EXCEPTION,
    
    /**
     * Return null when accessing corrupted preferences.
     */
    RETURN_NULL,
    
    /**
     * Return the default value when accessing corrupted preferences.
     */
    RETURN_DEFAULT,
    
    /**
     * Return a corrupted/invalid value when accessing corrupted preferences.
     */
    RETURN_CORRUPTED_VALUE
}

/**
 * Builder class for creating MockPreferencesConfiguration instances.
 */
class MockPreferencesConfigurationBuilder {
    private val initialValues = mutableMapOf<String, Any?>()
    private var simulateCorruption: Boolean = false
    private var corruptionBehavior: CorruptionBehavior = CorruptionBehavior.THROW_EXCEPTION
    private var corruptionAffectsGetAll: Boolean = true
    private val corruptionAffectsSpecificKeys = mutableSetOf<String>()
    private var editorConfiguration: MockEditorConfiguration = MockEditorConfiguration()

    /**
     * Add an initial preference value.
     */
    fun addInitialValue(key: String, value: Any?): MockPreferencesConfigurationBuilder {
        initialValues[key] = value
        return this
    }

    /**
     * Add multiple initial preference values.
     */
    fun addInitialValues(values: Map<String, Any?>): MockPreferencesConfigurationBuilder {
        initialValues.putAll(values)
        return this
    }

    /**
     * Enable corruption simulation.
     */
    fun simulateCorruption(
        behavior: CorruptionBehavior = CorruptionBehavior.THROW_EXCEPTION,
        affectsGetAll: Boolean = true
    ): MockPreferencesConfigurationBuilder {
        simulateCorruption = true
        corruptionBehavior = behavior
        corruptionAffectsGetAll = affectsGetAll
        return this
    }

    /**
     * Add keys that should be affected by corruption.
     */
    fun addCorruptedKeys(vararg keys: String): MockPreferencesConfigurationBuilder {
        corruptionAffectsSpecificKeys.addAll(keys)
        return this
    }

    /**
     * Set the editor configuration.
     */
    fun setEditorConfiguration(config: MockEditorConfiguration): MockPreferencesConfigurationBuilder {
        editorConfiguration = config
        return this
    }

    /**
     * Build the final configuration.
     */
    fun build(): MockPreferencesConfiguration {
        return MockPreferencesConfiguration(
            initialValues = initialValues.toMap(),
            simulateCorruption = simulateCorruption,
            corruptionBehavior = corruptionBehavior,
            corruptionAffectsGetAll = corruptionAffectsGetAll,
            corruptionAffectsSpecificKeys = corruptionAffectsSpecificKeys.toSet(),
            editorConfiguration = editorConfiguration
        )
    }
}

/**
 * Factory object for creating common mock configurations.
 * 
 * Provides pre-configured mock setups for common testing scenarios.
 */
object MockConfigurations {
    
    /**
     * Configuration for testing successful AI generation scenarios.
     */
    fun successfulGeneration(): MockApiConfiguration {
        return MockApiConfigurationBuilder()
            .setDefaultResponse(MockResponse.createSuccessResponse())
            .addResponse("create ad blocker", MockResponse.createSuccessResponse(
                name = "Ad Blocker",
                script = "document.querySelectorAll('[class*=\"ad\"]').forEach(el => el.remove());",
                explanation = "Removes advertisement elements from the page"
            ))
            .addResponse("translate page", MockResponse.createSuccessResponse(
                name = "Page Translator",
                script = "// Translation logic here",
                explanation = "Translates page content to selected language"
            ))
            .build()
    }
    
    /**
     * Configuration for testing network error scenarios.
     */
    fun networkErrors(): MockApiConfiguration {
        return MockApiConfigurationBuilder()
            .simulateNetworkError(NetworkErrorType.TIMEOUT)
            .build()
    }
    
    /**
     * Configuration for testing API key validation.
     */
    fun apiKeyErrors(): MockApiConfiguration {
        return MockApiConfigurationBuilder()
            .simulateApiKeyError()
            .build()
    }
    
    /**
     * Configuration for testing safety filter violations.
     */
    fun safetyViolations(): MockApiConfiguration {
        return MockApiConfigurationBuilder()
            .simulateSafetyViolation()
            .build()
    }
    
    /**
     * Configuration for testing JSON parsing edge cases.
     */
    fun jsonParsingTests(): MockApiConfiguration {
        return MockApiConfigurationBuilder()
            .addResponse("markdown", MockResponse.createMarkdownResponse())
            .addResponse("incomplete", MockResponse.createIncompleteResponse(includeScript = false))
            .addResponse("empty", MockResponse.createEmptyFieldsResponse())
            .addResponse("malformed", MockResponse.createMalformedJsonResponse())
            .addResponse("no_json", MockResponse.createNoJsonResponse())
            .addResponse("multiple", MockResponse.createMultipleJsonResponse())
            .addResponse("complex", MockResponse.createComplexJsonResponse())
            .addResponse("unicode", MockResponse.createUnicodeResponse())
            .build()
    }
    
    /**
     * Configuration for testing rate limiting scenarios.
     */
    fun rateLimiting(): MockApiConfiguration {
        return MockApiConfigurationBuilder()
            .simulateRateLimit()
            .build()
    }
    
    /**
     * Configuration with realistic delays for integration testing.
     */
    fun realisticTiming(): MockApiConfiguration {
        return MockApiConfigurationBuilder()
            .setDefaultResponse(MockResponse.createSuccessResponse())
            .setResponseDelay(1500L) // 1.5 second delay to simulate real API
            .build()
    }

    // ========== SharedPreferences Mock Configurations ==========

    /**
     * Configuration for testing successful API key storage and retrieval.
     */
    fun validApiKeyPreferences(): MockPreferencesConfiguration {
        return MockPreferencesConfigurationBuilder()
            .addInitialValue("gemini_api_key", "AIzaSyDummyValidApiKey123456789")
            .addInitialValue("sandbox_mode", true)
            .addInitialValue("simple_mode", false)
            .addInitialValue("first_run", false)
            .build()
    }

    /**
     * Configuration for testing missing API key scenarios.
     */
    fun missingApiKeyPreferences(): MockPreferencesConfiguration {
        return MockPreferencesConfigurationBuilder()
            .addInitialValue("sandbox_mode", true)
            .addInitialValue("simple_mode", false)
            .addInitialValue("first_run", true)
            // Note: gemini_api_key is intentionally missing
            .build()
    }

    /**
     * Configuration for testing empty API key scenarios.
     */
    fun emptyApiKeyPreferences(): MockPreferencesConfiguration {
        return MockPreferencesConfigurationBuilder()
            .addInitialValue("gemini_api_key", "")
            .addInitialValue("sandbox_mode", true)
            .addInitialValue("simple_mode", false)
            .build()
    }

    /**
     * Configuration for testing malformed API key scenarios.
     */
    fun malformedApiKeyPreferences(): MockPreferencesConfiguration {
        return MockPreferencesConfigurationBuilder()
            .addInitialValue("gemini_api_key", "invalid-key-format")
            .addInitialValue("sandbox_mode", true)
            .addInitialValue("simple_mode", false)
            .build()
    }

    /**
     * Configuration for testing preference corruption scenarios.
     */
    fun corruptedPreferences(): MockPreferencesConfiguration {
        return MockPreferencesConfigurationBuilder()
            .addInitialValue("gemini_api_key", "AIzaSyValidKey123")
            .simulateCorruption(CorruptionBehavior.THROW_EXCEPTION)
            .addCorruptedKeys("gemini_api_key")
            .build()
    }

    /**
     * Configuration for testing preference corruption with graceful degradation.
     */
    fun corruptedPreferencesWithFallback(): MockPreferencesConfiguration {
        return MockPreferencesConfigurationBuilder()
            .addInitialValue("gemini_api_key", "AIzaSyValidKey123")
            .simulateCorruption(CorruptionBehavior.RETURN_DEFAULT)
            .addCorruptedKeys("gemini_api_key")
            .build()
    }

    /**
     * Configuration for testing preference corruption that returns corrupted data.
     */
    fun corruptedPreferencesWithBadData(): MockPreferencesConfiguration {
        return MockPreferencesConfigurationBuilder()
            .addInitialValue("gemini_api_key", "AIzaSyValidKey123")
            .simulateCorruption(CorruptionBehavior.RETURN_CORRUPTED_VALUE)
            .addCorruptedKeys("gemini_api_key")
            .build()
    }

    /**
     * Configuration for testing editor write failures.
     */
    fun writeFailurePreferences(): MockPreferencesConfiguration {
        return MockPreferencesConfigurationBuilder()
            .setEditorConfiguration(
                MockEditorConfiguration(simulateWriteFailure = true)
            )
            .build()
    }

    /**
     * Configuration for testing editor commit failures.
     */
    fun commitFailurePreferences(): MockPreferencesConfiguration {
        return MockPreferencesConfigurationBuilder()
            .setEditorConfiguration(
                MockEditorConfiguration(simulateCommitFailure = true)
            )
            .build()
    }

    /**
     * Configuration for testing editor apply failures.
     */
    fun applyFailurePreferences(): MockPreferencesConfiguration {
        return MockPreferencesConfigurationBuilder()
            .setEditorConfiguration(
                MockEditorConfiguration(simulateApplyFailure = true)
            )
            .build()
    }

    /**
     * Configuration for testing slow preference operations.
     */
    fun slowPreferenceOperations(): MockPreferencesConfiguration {
        return MockPreferencesConfigurationBuilder()
            .addInitialValue("gemini_api_key", "AIzaSyValidKey123")
            .setEditorConfiguration(
                MockEditorConfiguration(
                    commitDelay = 1000L,
                    applyDelay = 500L
                )
            )
            .build()
    }

    /**
     * Configuration for testing complete application preferences state.
     */
    fun completeApplicationPreferences(): MockPreferencesConfiguration {
        return MockPreferencesConfigurationBuilder()
            .addInitialValues(mapOf(
                "gemini_api_key" to "AIzaSyCompleteValidApiKey123456789",
                "home_url" to "https://goonee.netlify.app/",
                "saved_shortcuts" to """[{"name":"Test","script":"alert('test');","explanation":"Test shortcut"}]""",
                "url_history" to """["https://example.com","https://google.com"]""",
                "saved_tabs" to """[{"id":"tab1","url":"https://example.com","title":"Example"}]""",
                "current_tab_id" to "tab1",
                "sandbox_mode" to true,
                "simple_mode" to false,
                "first_run" to false,
                "visit_counts" to """{"https://example.com":5,"https://google.com":3}"""
            ))
            .build()
    }

    /**
     * Configuration for testing first-run application state.
     */
    fun firstRunPreferences(): MockPreferencesConfiguration {
        return MockPreferencesConfigurationBuilder()
            .addInitialValue("first_run", true)
            .addInitialValue("sandbox_mode", true)
            .addInitialValue("simple_mode", false)
            // No API key set on first run
            .build()
    }

    /**
     * Configuration for testing preference recovery scenarios.
     */
    fun recoverableCorruptedPreferences(): MockPreferencesConfiguration {
        return MockPreferencesConfigurationBuilder()
            .addInitialValue("gemini_api_key", "AIzaSyRecoverableKey123")
            .simulateCorruption(CorruptionBehavior.RETURN_NULL)
            .addCorruptedKeys("gemini_api_key")
            .build()
    }
}