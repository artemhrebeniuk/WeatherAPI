package com.weatherapi.forecast.domain.usecase

import com.weatherapi.forecast.common.error.WeatherError
import com.weatherapi.forecast.domain.model.City
import com.weatherapi.forecast.domain.model.CityForecast
import com.weatherapi.forecast.domain.model.DateForecast
import com.weatherapi.forecast.domain.model.Humidity
import com.weatherapi.forecast.domain.model.Temperature
import com.weatherapi.forecast.domain.model.Wind
import com.weatherapi.forecast.domain.repository.WeatherRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GetForecastUseCaseTest {

    private val repository = mockk<WeatherRepository>()
    private val useCase = GetForecastUseCase(repository)

    @Test
    fun `execute retrieves all cities concurrently and combines successful results`() = runTest {
        val cities = listOf("Chisinau", "Madrid", "Kyiv", "Amsterdam")

        for (city in cities) {
            coEvery { repository.getForecast(city, 2) } returns Result.success(
                CityForecast(
                    city = City(city),
                    forecasts = listOf(
                        DateForecast(
                            date = "2026-09-10",
                            temperature = Temperature(12.0, 22.0),
                            humidity = Humidity(60),
                            wind = Wind(14.0, "W")
                        )
                    )
                )
            )
        }

        val result = useCase.execute(cities, 2)

        assertTrue(result.isFullySuccessful)
        assertEquals(4, result.successful.size)
        assertEquals(0, result.failures.size)
    }

    @Test
    fun `execute isolates failures using supervisorScope without aborting successful cities`() = runTest {
        val cities = listOf("Chisinau", "Madrid", "Kyiv", "Amsterdam")

        coEvery { repository.getForecast("Chisinau", 2) } returns Result.success(
            CityForecast(City("Chisinau"), emptyList())
        )
        coEvery { repository.getForecast("Madrid", 2) } returns Result.failure(
            WeatherError.NetworkError("Timeout connecting to Madrid")
        )
        coEvery { repository.getForecast("Kyiv", 2) } returns Result.success(
            CityForecast(City("Kyiv"), emptyList())
        )
        coEvery { repository.getForecast("Amsterdam", 2) } returns Result.success(
            CityForecast(City("Amsterdam"), emptyList())
        )

        val result = useCase.execute(cities, 2)

        assertFalse(result.isFullySuccessful)
        assertTrue(result.hasAnySuccess)
        assertEquals(3, result.successful.size)
        assertEquals(1, result.failures.size)
        assertEquals("Madrid", result.failures[0].city)
    }

    @Test
    fun `execute traps unexpected unhandled exception thrown in child coroutine without crashing batch`() = runTest {
        val cities = listOf("Kyiv", "Madrid")

        coEvery { repository.getForecast("Kyiv", 2) } returns Result.success(
            CityForecast(City("Kyiv"), emptyList())
        )
        coEvery { repository.getForecast("Madrid", 2) } throws RuntimeException("Severe unexpected hardware/OOM crash")

        val result = useCase.execute(cities, 2)

        assertTrue(result.hasAnySuccess)
        assertEquals(1, result.successful.size)
        assertEquals(1, result.failures.size)
        assertEquals("Madrid", result.failures[0].city)
        assertEquals("Severe unexpected hardware/OOM crash", result.failures[0].error.message)
    }
}
