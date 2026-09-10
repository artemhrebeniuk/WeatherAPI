package com.weatherapi.forecast.common.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AppConfigTest {

    private fun createEnvProvider(
        env: Map<String, String> = emptyMap(),
        props: Map<String, String> = emptyMap()
    ): EnvironmentProvider = object : EnvironmentProvider {
        override fun getEnv(name: String): String? = env[name]
        override fun getProperty(name: String): String? = props[name]
    }

    @Test
    fun `resolveApiKey prioritizes explicit CLI argument over env and system property`() {
        val envProvider = createEnvProvider(
            env = mapOf(AppConfig.ENV_API_KEY to "env_key"),
            props = mapOf(AppConfig.PROP_API_KEY to "system_property_key")
        )

        val resolved = AppConfig.resolveApiKey("cli_arg_key", envProvider)

        assertEquals("cli_arg_key", resolved)
    }

    @Test
    fun `resolveApiKey prioritizes environment variable over system property when CLI argument is missing`() {
        val envProvider = createEnvProvider(
            env = mapOf(AppConfig.ENV_API_KEY to "env_key"),
            props = mapOf(AppConfig.PROP_API_KEY to "system_property_key")
        )

        val fromNull = AppConfig.resolveApiKey(null, envProvider)
        val fromBlank = AppConfig.resolveApiKey("   ", envProvider)

        assertEquals("env_key", fromNull)
        assertEquals("env_key", fromBlank)
    }

    @Test
    fun `resolveApiKey falls back to system property when CLI argument and env variable are missing`() {
        val envProvider = createEnvProvider(
            props = mapOf(AppConfig.PROP_API_KEY to "system_property_key")
        )

        val fromNull = AppConfig.resolveApiKey(null, envProvider)
        val fromBlank = AppConfig.resolveApiKey("   ", envProvider)

        assertEquals("system_property_key", fromNull)
        assertEquals("system_property_key", fromBlank)
    }

    @Test
    fun `resolveApiKey returns null when all sources are missing or blank`() {
        val envProvider = createEnvProvider(
            env = mapOf(AppConfig.ENV_API_KEY to "  "),
            props = mapOf(AppConfig.PROP_API_KEY to "")
        )

        val resolved = AppConfig.resolveApiKey("   ", envProvider)
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
