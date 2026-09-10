package com.weatherapi.forecast.data.remote

import com.weatherapi.forecast.common.error.WeatherError
import com.weatherapi.forecast.data.remote.api.WeatherApiService
import com.weatherapi.forecast.data.remote.dto.ForecastResponseDto
import com.weatherapi.forecast.data.remote.interceptor.ApiKeyInterceptor
import com.weatherapi.forecast.data.repository.WeatherRepositoryImpl
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class WeatherApiIntegrationTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: WeatherRepositoryImpl
    private val testApiKey = "test_api_key_xyz"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(ApiKeyInterceptor(testApiKey))
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val apiService = retrofit.create(WeatherApiService::class.java)
        repository = WeatherRepositoryImpl(apiService, json)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getForecast sends authentic request and deserializes full forecast response`() = runTest {
        val sampleJsonResponse = """
            {
              "location": {
                "name": "Chisinau",
                "region": "Chisinau",
                "country": "Moldova",
                "lat": 47.01,
                "lon": 28.86,
                "tz_id": "Europe/Chisinau",
                "localtime_epoch": 1725916800,
                "localtime": "2026-09-09 23:00"
              },
              "current": {
                "temp_c": 19.5,
                "humidity": 65,
                "wind_kph": 11.2,
                "wind_degree": 45,
                "wind_dir": "NE"
              },
              "forecast": {
                "forecastday": [
                  {
                    "date": "2026-09-09",
                    "day": {
                      "maxtemp_c": 24.0,
                      "mintemp_c": 13.0,
                      "avghumidity": 58.0,
                      "maxwind_kph": 14.0
                    },
                    "hour": []
                  },
                  {
                    "date": "2026-09-10",
                    "day": {
                      "maxtemp_c": 26.2,
                      "mintemp_c": 15.1,
                      "avghumidity": 60.0,
                      "maxwind_kph": 16.5
                    },
                    "hour": [
                      { "wind_kph": 12.0, "wind_degree": 270, "wind_dir": "W" },
                      { "wind_kph": 14.0, "wind_degree": 275, "wind_dir": "W" }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(sampleJsonResponse)
        )

        val result = repository.getForecast("Chisinau", 2)

        assertTrue(result.isSuccess)
        val forecast = result.getOrThrow()
        assertEquals("Chisinau", forecast.city.name)
        assertEquals(2, forecast.forecasts.size)

        val nextDay = forecast.nextDayForecast!!
        assertEquals("2026-09-10", nextDay.date)
        assertEquals(15.1, nextDay.temperature.minCelsius)
        assertEquals(26.2, nextDay.temperature.maxCelsius)
        assertEquals(60, nextDay.humidity.percentage)
        assertEquals(16.5, nextDay.wind.maxSpeedKph)
        assertEquals("W", nextDay.wind.prevailingDirection)

        // Verify request parameters received by server
        val recordedRequest = mockWebServer.takeRequest()
        val requestUrl = recordedRequest.requestUrl!!
        assertEquals(testApiKey, requestUrl.queryParameter("key"))
        assertEquals("Chisinau", requestUrl.queryParameter("q"))
        assertEquals("2", requestUrl.queryParameter("days"))
    }

    @Test
    fun `getForecast fails when WeatherAPI returns empty forecast list`() = runTest {
        val emptyForecastJson = """
            {
              "location": { "name": "Madrid" },
              "forecast": { "forecastday": [] }
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(emptyForecastJson)
        )

        val result = repository.getForecast("Madrid", 2)

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertInstanceOf(WeatherError.GenericApiError::class.java, ex)
        assertTrue(ex?.message?.contains("empty forecast list") == true)
    }

    @Test
    fun `getForecast does not swallow CancellationException`() {
        val mockService = mockk<WeatherApiService>()
        coEvery { mockService.getForecast(any(), any(), any(), any()) } throws CancellationException("Coroutine cancelled")

        val repo = WeatherRepositoryImpl(mockService, json)

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.getForecast("Kyiv", 2)
            }
        }
    }

    @Test
    fun `getForecast handles 401 Unauthorized gracefully as InvalidApiKey error`() = runTest {
        val errorJson = """
            {
              "error": {
                "code": 2006,
                "message": "API key provided is invalid"
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody(errorJson)
        )

        val result = repository.getForecast("Madrid", 2)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertInstanceOf(WeatherError.InvalidApiKey::class.java, error)
    }

    @Test
    fun `getForecast handles 400 with code 1006 as CityNotFound error`() = runTest {
        val errorJson = """
            {
              "error": {
                "code": 1006,
                "message": "No matching location found."
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody(errorJson)
        )

        val result = repository.getForecast("NonExistentCityXYZ", 2)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertInstanceOf(WeatherError.CityNotFound::class.java, error)
        assertEquals("NonExistentCityXYZ", (error as WeatherError.CityNotFound).city)
    }

    @Test
    fun `getForecast handles 429 Rate Limit as QuotaExceeded error`() = runTest {
        val errorJson = """
            {
              "error": {
                "code": 2007,
                "message": "API key has exceeded calls per month quota."
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody(errorJson)
        )

        val result = repository.getForecast("Kyiv", 2)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertInstanceOf(WeatherError.QuotaExceeded::class.java, error)
    }

    @Test
    fun `getForecast handles code 2008 as InvalidApiKey when key has been disabled`() = runTest {
        val errorJson = """
            {
              "error": {
                "code": 2008,
                "message": "API key has been disabled."
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody(errorJson)
        )

        val result = repository.getForecast("Kyiv", 2)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertInstanceOf(WeatherError.InvalidApiKey::class.java, error)
    }


    @Test
    fun `getForecast maps malformed JSON to SerializationError`() = runTest {
        val corruptedJson = """{ "location": { "name": "Madrid" }, "forecast": { "forecastday": [ { "date": }}}"""

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(corruptedJson)
        )

        val result = repository.getForecast("Madrid", 2)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertInstanceOf(WeatherError.SerializationError::class.java, error)
        assertTrue(error?.message?.contains("Failed to deserialize WeatherAPI response") == true)
    }

    @Test
    fun `getForecast maps SocketTimeoutException to NetworkError`() = runTest {
        val mockService = mockk<WeatherApiService>()
        coEvery { mockService.getForecast(any(), any(), any(), any()) } throws java.net.SocketTimeoutException("Read timed out")

        val repo = WeatherRepositoryImpl(mockService, json)
        val result = repo.getForecast("Kyiv", 2)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertInstanceOf(WeatherError.NetworkError::class.java, error)
        assertTrue(error?.message?.contains("timed out") == true)
    }

    @Test
    fun `getForecast handles HTTP 200 with error body as API error`() = runTest {
        val errorJson = """
            {
              "error": {
                "code": 2006,
                "message": "API key provided is invalid"
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(errorJson)
        )

        val result = repository.getForecast("Madrid", 2)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertInstanceOf(WeatherError.InvalidApiKey::class.java, error)
        assertTrue(error?.message?.contains("API key provided is invalid") == true)
    }

    @Test
    fun `getForecast handles partial JSON response with null location name and missing day gracefully`() = runTest {
        val partialJson = """
            {
              "location": {
                "localtime": "2026-09-11 12:00"
              },
              "forecast": {
                "forecastday": [
                  {
                    "date": "2026-09-12",
                    "day": null,
                    "hour": []
                  }
                ]
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(partialJson)
        )

        val result = repository.getForecast("Chisinau", 2)
        assertTrue(result.isSuccess)
        val forecast = result.getOrNull()
        assertEquals("Chisinau", forecast?.city?.name)
        assertEquals(1, forecast?.forecasts?.size)
    }
}
