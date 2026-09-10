package com.weatherapi.forecast.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.min
import kotlin.math.pow

/**
 * Abstraction for thread suspension during backoff, allowing tests
 * to execute instantly without blocking real wall-clock time.
 */
fun interface Sleeper {
    fun sleep(millis: Long)

    companion object {
        val DEFAULT: Sleeper = Sleeper { Thread.sleep(it) }
    }
}

/**
 * Resilient OkHttp interceptor that automatically retries failed network requests
 * on transient server outages (HTTP 408, 500, 502, 503, 504) with exponential backoff
 * and Full Jitter, closing response bodies promptly to avoid connection pool starvation.
 */
class RetryInterceptor(
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
    private val initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
    private val maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
    private val sleeper: Sleeper = Sleeper.DEFAULT
) : Interceptor {

    companion object {
        const val DEFAULT_MAX_RETRIES = 3
        const val DEFAULT_INITIAL_DELAY_MS = 300L
        const val DEFAULT_MAX_DELAY_MS = 3000L
        private val RETRYABLE_HTTP_CODES = setOf(408, 500, 502, 503, 504)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var lastException: IOException? = null

        for (attempt in 0..maxRetries) {
            val response: Response
            try {
                response = chain.proceed(request)
            } catch (ioe: IOException) {
                lastException = ioe
                if (attempt == maxRetries || !isTransientNetworkException(ioe)) throw ioe
                performBackoff(attempt)
                continue
            }

            // If success, non-retryable status, or retries exhausted, return response immediately
            if (response.code !in RETRYABLE_HTTP_CODES || attempt == maxRetries) {
                return response
            }

            // CRITICAL: Explicitly close response body BEFORE sleeping to avoid connection starvation
            response.close()
            performBackoff(attempt)
        }

        throw (lastException ?: IOException("Failed to execute request after $maxRetries retries"))
    }

    private fun performBackoff(attempt: Int) {
        val exponentialCap = min(maxDelayMs, (initialDelayMs * 2.0.pow(attempt.toDouble())).toLong())
        val sleepTimeMs = if (exponentialCap <= 10L) 10L else ThreadLocalRandom.current().nextLong(10L, exponentialCap + 1L)

        try {
            sleeper.sleep(sleepTimeMs)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("HTTP request retry interrupted")
        }
    }

    private fun isTransientNetworkException(ioe: IOException): Boolean {
        return when (ioe) {
            is java.net.SocketTimeoutException,
            is java.net.ConnectException -> true
            is java.net.UnknownHostException,
            is javax.net.ssl.SSLException -> false
            is java.net.SocketException -> {
                // Catches transient socket drops: "Connection reset", "Software caused connection abort", "Broken pipe"
                val msg = ioe.message?.lowercase().orEmpty()
                msg.contains("reset") || msg.contains("abort") || msg.contains("broken pipe")
            }
            else -> ioe.message?.contains("unexpected end of stream", ignoreCase = true) == true
        }
    }
}
