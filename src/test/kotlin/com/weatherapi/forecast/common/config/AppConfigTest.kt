package com.weatherapi.forecast.common.config

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AppConfigTest {

    private var previousApiKeyProperty: String? = null

    @BeforeEach
    fun setUp() {
        previousApiKeyProperty = System.getProperty(AppConfig.PROP_API_KEY)
    }

    @AfterEach
    fun tearDown() {
        if (previousApiKeyProperty != null) {
            System.setProperty(AppConfig.PROP_API_KEY, previousApiKeyProperty!!)
        } else {
            System.clearProperty(AppConfig.PROP_API_KEY)
        }
    }

    @Test
    fun `resolveApiKey prioritizes explicit CLI argument over system property`() {
        System.setProperty(AppConfig.PROP_API_KEY, "system_property_key")

        val resolved = AppConfig.resolveApiKey("cli_arg_key")

        assertEquals("cli_arg_key", resolved)
    }

    @Test
    fun `resolveApiKey falls back to system property when CLI argument is null or blank`() {
        System.setProperty(AppConfig.PROP_API_KEY, "system_property_key")

        val fromNull = AppConfig.resolveApiKey(null)
        val fromBlank = AppConfig.resolveApiKey("   ")

        assertEquals("system_property_key", fromNull)
        assertEquals("system_property_key", fromBlank)
    }

    @Test
    fun `resolveApiKey returns null when all sources are missing or blank`() {
        System.clearProperty(AppConfig.PROP_API_KEY)

        org.junit.jupiter.api.Assumptions.assumeTrue(
            System.getenv(AppConfig.ENV_API_KEY).isNullOrBlank(),
            "Test requires WEATHER_API_KEY environment variable to be unset"
        )

        val resolved = AppConfig.resolveApiKey("   ")
        assertNull(resolved)
    }

    @Test
    fun `AppConfig initializes with default constants`() {
        val config = AppConfig(apiKey = "test_key")

        assertEquals("test_key", config.apiKey)
        assertEquals(AppConfig.DEFAULT_BASE_URL, config.baseUrl)
        assertEquals(listOf("Chisinau", "Madrid", "Kyiv", "Amsterdam"), config.targetCities)
        assertEquals(3, config.days)
        assertEquals(10L, config.connectTimeoutSeconds)
        assertEquals(15L, config.readTimeoutSeconds)
    }
}
