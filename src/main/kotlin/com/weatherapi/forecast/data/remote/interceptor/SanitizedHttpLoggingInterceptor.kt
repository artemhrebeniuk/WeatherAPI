package com.weatherapi.forecast.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Factory for OkHttp logging interceptor that automatically sanitizes and redacts
 * sensitive query parameters (such as the WeatherAPI key) before emitting log lines.
 */
object SanitizedHttpLoggingInterceptor {

    private val SENSITIVE_KEY_REGEX = Regex("([?&]key=)([^&\\s]+)", RegexOption.IGNORE_CASE)

    fun create(enabled: Boolean): Interceptor {
        val logger = HttpLoggingInterceptor { message ->
            val sanitized = message.replace(SENSITIVE_KEY_REGEX, "$1***REDACTED***")
            System.err.println("[HTTP] $sanitized")
        }.apply {
            level = if (enabled) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return logger
    }
}
