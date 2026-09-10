package com.weatherapi.forecast.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.min
import kotlin.math.pow

/**
 * Resilient OkHttp interceptor that automatically retries failed network requests
 * on transient server outages (HTTP 502, 503, 504) or rate limits (HTTP 429)
 * with exponential backoff and Full Jitter to mitigate thundering herd spikes.
 */
class RetryInterceptor(
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
    private val initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
    private val maxDelayMs: Long = DEFAULT_MAX_DELAY_MS
) : Interceptor {

    companion object {
        const val DEFAULT_MAX_RETRIES = 3
        const val DEFAULT_INITIAL_DELAY_MS = 300L
        const val DEFAULT_MAX_DELAY_MS = 3000L
        private val RETRYABLE_HTTP_CODES = setOf(429, 502, 503, 504)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response: Response? = null
        var lastException: IOException? = null

        for (attempt in 0..maxRetries) {
            try {
                response?.close()
                response = chain.proceed(request)

                // Return immediately if response is not a transient server error or if retries exhausted
                if (response.code !in RETRYABLE_HTTP_CODES || attempt == maxRetries) {
                    return response
                }
            } catch (ioe: IOException) {
                lastException = ioe
                if (attempt == maxRetries) throw ioe
            }

            // Exponential backoff with Full Jitter: uniform_random(10, min(maxDelay, initialDelay * 2^attempt))
            val exponentialCap = min(maxDelayMs, (initialDelayMs * 2.0.pow(attempt.toDouble())).toLong())
            val sleepTimeMs = if (exponentialCap <= 10L) 10L else ThreadLocalRandom.current().nextLong(10L, exponentialCap + 1L)

            try {
                Thread.sleep(sleepTimeMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw lastException ?: IOException("Request retry interrupted")
            }
        }

        return response ?: throw (lastException ?: IOException("Failed after $maxRetries retries"))
    }
}
