package com.weatherapi.forecast.data.remote.interceptor

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class RetryInterceptorTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `retries on 503 and succeeds on subsequent attempt`() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("Service Unavailable"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        val client = OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor(maxRetries = 2, initialDelayMs = 10L))
            .build()

        val request = Request.Builder().url(server.url("/test")).build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals("OK", response.body?.string())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `returns 504 when retries are exhausted`() {
        server.enqueue(MockResponse().setResponseCode(504).setBody("Gateway Timeout"))
        server.enqueue(MockResponse().setResponseCode(504).setBody("Gateway Timeout"))
        server.enqueue(MockResponse().setResponseCode(504).setBody("Gateway Timeout"))

        val client = OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor(maxRetries = 2, initialDelayMs = 10L))
            .build()

        val request = Request.Builder().url(server.url("/test")).build()
        val response = client.newCall(request).execute()

        assertEquals(504, response.code)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `does not retry on 429 quota or rate limit exceeded`() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("Rate limit exceeded"))

        val client = OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor(maxRetries = 2, initialDelayMs = 10L))
            .build()

        val request = Request.Builder().url(server.url("/test")).build()
        val response = client.newCall(request).execute()

        assertEquals(429, response.code)
        assertEquals(1, server.requestCount)
        response.close()
    }

    @Test
    fun `sanitizes sensitive key in HTTP logs`() {
        val originalErr = System.err
        val errBuffer = ByteArrayOutputStream()
        System.setErr(PrintStream(errBuffer))

        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

            val client = OkHttpClient.Builder()
                .addInterceptor(SanitizedHttpLoggingInterceptor.create(enabled = true))
                .build()

            val request = Request.Builder()
                .url(server.url("/v1/forecast.json?q=Chisinau&key=super_secret_12345"))
                .build()

            val response = client.newCall(request).execute()
            assertEquals(200, response.code)

            val logOutput = errBuffer.toString()
            assertTrue(logOutput.contains("***REDACTED***"), "Log output should contain redacted placeholder")
            assertTrue(!logOutput.contains("super_secret_12345"), "Log output must never leak raw secret key")
        } finally {
            System.setErr(originalErr)
        }
    }
}
