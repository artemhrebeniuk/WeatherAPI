package com.weatherapi.forecast.common.config

/**
 * Immutable configuration for the Weather Forecast application.
 *
 * Supports configuration resolution priority:
 * 1. Explicit CLI arguments
 * 2. Environment variables (WEATHER_API_KEY)
 * 3. System properties (weather.api.key)
 */
/**
 * Provider interface abstracting environment variables and system properties
 * to allow deterministic, hermetic unit testing without mutating global JVM state.
 */
interface EnvironmentProvider {
    fun getEnv(name: String): String?
    fun getProperty(name: String): String?

    companion object {
        val SYSTEM: EnvironmentProvider = object : EnvironmentProvider {
            override fun getEnv(name: String): String? = System.getenv(name)
            override fun getProperty(name: String): String? = System.getProperty(name)
        }
    }
}

data class AppConfig(
    val apiKey: String,
    val baseUrl: String = DEFAULT_BASE_URL,
    val targetCities: List<String> = DEFAULT_CITIES,
    val days: Int = DEFAULT_FORECAST_DAYS,
    val connectTimeoutSeconds: Long = DEFAULT_CONNECT_TIMEOUT_SECONDS,
    val readTimeoutSeconds: Long = DEFAULT_READ_TIMEOUT_SECONDS
) {
    companion object {
        const val DEFAULT_BASE_URL: String = "https://api.weatherapi.com/v1/"
        val DEFAULT_CITIES: List<String> = listOf("Chisinau", "Madrid", "Kyiv", "Amsterdam")
        const val DEFAULT_FORECAST_DAYS: Int = 3
        const val DEFAULT_CONNECT_TIMEOUT_SECONDS: Long = 10L
        const val DEFAULT_READ_TIMEOUT_SECONDS: Long = 15L

        const val ENV_API_KEY: String = "WEATHER_API_KEY"
        const val PROP_API_KEY: String = "weather.api.key"

        /**
         * Resolves the API key with cascading priority:
         * 1. CLI argument
         * 2. Environment variable (WEATHER_API_KEY)
         * 3. System property (weather.api.key)
         */
        fun resolveApiKey(
            cliApiKey: String?,
            envProvider: EnvironmentProvider = EnvironmentProvider.SYSTEM
        ): String? {
            return cliApiKey?.takeIf { it.isNotBlank() }
                ?: envProvider.getEnv(ENV_API_KEY)?.takeIf { it.isNotBlank() }
                ?: envProvider.getProperty(PROP_API_KEY)?.takeIf { it.isNotBlank() }
        }
    }
}
