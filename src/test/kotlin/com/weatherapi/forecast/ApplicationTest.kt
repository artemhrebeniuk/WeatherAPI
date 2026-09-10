package com.weatherapi.forecast

import com.weatherapi.forecast.common.config.EnvironmentProvider
import com.weatherapi.forecast.domain.model.City
import com.weatherapi.forecast.domain.model.CityForecast
import com.weatherapi.forecast.domain.model.DateForecast
import com.weatherapi.forecast.domain.model.Humidity
import com.weatherapi.forecast.domain.model.Temperature
import com.weatherapi.forecast.domain.model.Wind
import com.weatherapi.forecast.domain.repository.WeatherRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.LocalDate

class ApplicationTest {

    private val emptyEnvProvider = object : EnvironmentProvider {
        override fun getEnv(name: String): String? = null
        override fun getProperty(name: String): String? = null
    }

    @Test
    fun `runApplication returns 0 and prints help when help requested`() {
        val outBytes = ByteArrayOutputStream()
        val errBytes = ByteArrayOutputStream()
        val stdout = PrintStream(outBytes)
        val stderr = PrintStream(errBytes)

        val code = runApplication(arrayOf("--help"), stdout, stderr, emptyEnvProvider)

        assertEquals(0, code)
        assertTrue(outBytes.toString().contains("USAGE:"))
        assertTrue(outBytes.toString().contains("OPTIONS:"))
    }

    @Test
    fun `runApplication returns 0 and prints version when version requested`() {
        val outBytes = ByteArrayOutputStream()
        val errBytes = ByteArrayOutputStream()
        val stdout = PrintStream(outBytes)
        val stderr = PrintStream(errBytes)

        val code = runApplication(arrayOf("--version"), stdout, stderr, emptyEnvProvider)

        assertEquals(0, code)
        assertTrue(outBytes.toString().contains("WeatherAPI Forecast CLI version 1.0.0"))
    }

    @Test
    fun `runApplication returns 1 and writes to stderr on parsing error`() {
        val outBytes = ByteArrayOutputStream()
        val errBytes = ByteArrayOutputStream()
        val stdout = PrintStream(outBytes)
        val stderr = PrintStream(errBytes)

        val code = runApplication(arrayOf("--unknown-option"), stdout, stderr, emptyEnvProvider)

        assertEquals(1, code)
        assertTrue(errBytes.toString().contains("[ERROR] Unrecognized option '--unknown-option'"))
    }

    @Test
    fun `runApplication returns 1 when api key is missing`() {
        val outBytes = ByteArrayOutputStream()
        val errBytes = ByteArrayOutputStream()
        val stdout = PrintStream(outBytes)
        val stderr = PrintStream(errBytes)

        val code = runApplication(arrayOf("--cities=Kyiv"), stdout, stderr, emptyEnvProvider)

        assertEquals(1, code)
        assertTrue(errBytes.toString().contains("[ERROR] API key is missing!"))
    }

    @Test
    fun `runApplication returns 1 on invalid date argument`() {
        val outBytes = ByteArrayOutputStream()
        val errBytes = ByteArrayOutputStream()
        val stdout = PrintStream(outBytes)
        val stderr = PrintStream(errBytes)

        val code = runApplication(arrayOf("--api-key=test", "--date=invalid-date"), stdout, stderr, emptyEnvProvider)

        assertEquals(1, code)
        assertTrue(errBytes.toString().contains("Invalid --date format 'invalid-date'"))
    }

    @Test
    fun `runApplication returns 0 and outputs table when repository succeeds`() {
        val outBytes = ByteArrayOutputStream()
        val errBytes = ByteArrayOutputStream()
        val stdout = PrintStream(outBytes)
        val stderr = PrintStream(errBytes)

        val mockRepo = object : WeatherRepository {
            override suspend fun getForecast(city: String, days: Int): Result<CityForecast> {
                val forecast = DateForecast(
                    date = "2026-09-11",
                    temperature = Temperature(10.0, 20.0),
                    humidity = Humidity(50),
                    wind = Wind(15.0, "NW")
                )
                return Result.success(
                    CityForecast(
                        city = City(name = city),
                        localDate = LocalDate.parse("2026-09-10"),
                        forecasts = listOf(forecast)
                    )
                )
            }
        }

        val code = runApplication(
            args = arrayOf("--api-key=dummy_key", "--cities=Chisinau,Madrid"),
            stdout = stdout,
            stderr = stderr,
            envProvider = emptyEnvProvider,
            repositoryOverride = mockRepo
        )

        assertEquals(0, code)
        assertTrue(outBytes.toString().contains("Chisinau"))
        assertTrue(outBytes.toString().contains("Madrid"))
        assertTrue(outBytes.toString().contains("2026-09-11"))
        assertTrue(errBytes.toString().isEmpty())
    }

    @Test
    fun `runApplication deduplicates cities case-insensitively`() {
        val outBytes = ByteArrayOutputStream()
        val errBytes = ByteArrayOutputStream()
        val stdout = PrintStream(outBytes)
        val stderr = PrintStream(errBytes)

        val requestedCities = mutableListOf<String>()
        val mockRepo = object : WeatherRepository {
            override suspend fun getForecast(city: String, days: Int): Result<CityForecast> {
                requestedCities.add(city)
                val forecast = DateForecast(
                    date = "2026-09-11",
                    temperature = Temperature(10.0, 20.0),
                    humidity = Humidity(50),
                    wind = Wind(15.0, "NW")
                )
                return Result.success(
                    CityForecast(
                        city = City(name = city),
                        localDate = LocalDate.parse("2026-09-10"),
                        forecasts = listOf(forecast)
                    )
                )
            }
        }

        val code = runApplication(
            args = arrayOf("--api-key=dummy_key", "--cities=Kyiv, kyiv, KYIV"),
            stdout = stdout,
            stderr = stderr,
            envProvider = emptyEnvProvider,
            repositoryOverride = mockRepo
        )

        assertEquals(0, code)
        assertEquals(listOf("Kyiv"), requestedCities)
    }

    @Test
    fun `runApplication returns 2 on partial failure when some cities fail`() {
        val outBytes = ByteArrayOutputStream()
        val errBytes = ByteArrayOutputStream()
        val stdout = PrintStream(outBytes)
        val stderr = PrintStream(errBytes)

        val mockRepo = object : WeatherRepository {
            override suspend fun getForecast(city: String, days: Int): Result<CityForecast> {
                if (city == "NonExistentCity") {
                    return Result.failure(com.weatherapi.forecast.common.error.WeatherError.CityNotFound(city))
                }
                val forecast = DateForecast(
                    date = "2026-09-11",
                    temperature = Temperature(10.0, 20.0),
                    humidity = Humidity(50),
                    wind = Wind(15.0, "NW")
                )
                return Result.success(
                    CityForecast(
                        city = City(name = city),
                        localDate = LocalDate.parse("2026-09-10"),
                        forecasts = listOf(forecast)
                    )
                )
            }
        }

        val code = runApplication(
            args = arrayOf("--api-key=dummy_key", "--cities=Chisinau,NonExistentCity"),
            stdout = stdout,
            stderr = stderr,
            envProvider = emptyEnvProvider,
            repositoryOverride = mockRepo
        )

        assertEquals(2, code)
        assertTrue(outBytes.toString().contains("Chisinau"))
        assertTrue(errBytes.toString().contains("[WARNING] Some cities could not be retrieved:"))
        assertTrue(errBytes.toString().contains("NonExistentCity"))
    }
}
