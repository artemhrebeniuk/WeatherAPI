package com.weatherapi.forecast.data.remote

import com.weatherapi.forecast.common.config.AppConfig
import com.weatherapi.forecast.data.remote.api.WeatherApiService
import com.weatherapi.forecast.data.remote.interceptor.ApiKeyInterceptor
import com.weatherapi.forecast.data.repository.WeatherRepositoryImpl
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Optional Live End-to-End Test executing real network requests against WeatherAPI.com.
 *
 * Runs automatically when WEATHER_API_KEY is defined in the environment or system property;
 * gracefully skips if no API key is present so offline builds and CI do not fail.
 */
class WeatherApiLiveE2ETest {

    @Test
    fun `live API call retrieves real forecast for Chisinau, Madrid, Kyiv, Amsterdam`() = runTest {
        val apiKey = AppConfig.resolveApiKey(null)
        val isDummy = apiKey?.contains("dummy", ignoreCase = true) == true
        assumeTrue(
            !apiKey.isNullOrBlank() && !isDummy,
            "Live E2E test skipped: no valid WeatherAPI key provided in environment or system property."
        )

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(ApiKeyInterceptor(apiKey!!))
            .build()

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.weatherapi.com/v1/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val service = retrofit.create(WeatherApiService::class.java)
        val repository = WeatherRepositoryImpl(service, json)

        val targetCities = listOf("Chisinau", "Madrid", "Kyiv", "Amsterdam")
        
        // Verify key validity before asserting to prevent failing offline/unauthenticated environments
        val probeResult = repository.getForecast(targetCities.first(), 2)
        assumeTrue(
            probeResult.isSuccess,
            "Live E2E test skipped: API key rejected or network unavailable: ${probeResult.exceptionOrNull()?.message}"
        )

        for (city in targetCities) {
            val result = if (city == targetCities.first()) probeResult else repository.getForecast(city, 2)
            assertTrue(
                result.isSuccess,
                "Expected successful live forecast for $city, but got error: ${result.exceptionOrNull()?.message}"
            )
            val cityForecast = result.getOrThrow()
            assertNotNull(cityForecast.nextDayForecast, "Expected next-day forecast for $city")
            val nextDay = cityForecast.nextDayForecast!!
            assertTrue(nextDay.temperature.minCelsius <= nextDay.temperature.maxCelsius)
            assertTrue(nextDay.humidity.percentage in 0..100)
            assertTrue(nextDay.wind.maxSpeedKph >= 0.0)
            assertTrue(nextDay.wind.prevailingDirection.isNotBlank())
        }
    }
}
