package com.weatherapi.forecast.common.config

/**
 * Immutable configuration for the Weather Forecast application.
 *
 * Supports configuration resolution priority:
 * 1. Explicit CLI arguments
 * 2. Environment variables (WEATHER_API_KEY)
 * 3. System properties (weather.api.key)
 */
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
         * Resolves the API key from CLI arguments, environment variable, or system property.
         */
        fun resolveApiKey(cliApiKey: String?): String? {
            return cliApiKey?.takeIf { it.isNotBlank() }
                ?: System.getenv(ENV_API_KEY)?.takeIf { it.isNotBlank() }
                ?: System.getProperty(PROP_API_KEY)?.takeIf { it.isNotBlank() }
        }
    }
}
