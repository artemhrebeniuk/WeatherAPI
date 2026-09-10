package com.weatherapi.forecast.common.error

/**
 * Sealed hierarchy representing all domain and infrastructure errors
 * that can occur during weather forecast retrieval and processing.
 *
 * Inherits from [RuntimeException] to provide unchecked exception semantics
 * in Kotlin and smooth interoperability with JVM consumers.
 */
sealed class WeatherError(
    override val message: String,
    override val cause: Throwable? = null
) : RuntimeException(message, cause) {

    /**
     * Thrown when no API key was provided via CLI argument, environment variable, or system property.
     */
    data class ApiKeyMissing(
        override val message: String = "WeatherAPI key is missing. Please provide it via '--api-key=<KEY>', the WEATHER_API_KEY environment variable, or -Dweather.api.key system property.",
        override val cause: Throwable? = null
    ) : WeatherError(message, cause)

    /**
     * Thrown when the provided API key is invalid or disabled (HTTP 401 / 403).
     */
    data class InvalidApiKey(
        override val message: String = "Authentication failed: The provided WeatherAPI key is invalid or inactive.",
        override val cause: Throwable? = null
    ) : WeatherError(message, cause)

    /**
     * Thrown when the API key has exceeded its monthly/daily request quota (HTTP 429).
     */
    data class QuotaExceeded(
        override val message: String = "API rate limit exceeded or quota exhausted.",
        override val cause: Throwable? = null
    ) : WeatherError(message, cause)

    /**
     * Thrown when the requested location was not found by WeatherAPI (HTTP 400 error code 1006).
     */
    data class CityNotFound(
        val city: String,
        override val message: String = "Location '$city' was not found by WeatherAPI.",
        override val cause: Throwable? = null
    ) : WeatherError(message, cause)

    /**
     * Thrown when a network connectivity issue, DNS failure, or connection timeout occurs.
     */
    data class NetworkError(
        override val message: String = "Failed to connect to WeatherAPI. Please check your network connection.",
        override val cause: Throwable? = null
    ) : WeatherError(message, cause)

    /**
     * Thrown when the server response cannot be deserialized or has an unexpected format.
     */
    data class SerializationError(
        override val message: String = "Failed to parse response from WeatherAPI.",
        override val cause: Throwable? = null
    ) : WeatherError(message, cause)

    /**
     * Generic/unclassified error with HTTP status code and response body message.
     */
    data class GenericApiError(
        val httpCode: Int?,
        val apiErrorCode: Int?,
        override val message: String,
        override val cause: Throwable? = null
    ) : WeatherError(message, cause)
}
