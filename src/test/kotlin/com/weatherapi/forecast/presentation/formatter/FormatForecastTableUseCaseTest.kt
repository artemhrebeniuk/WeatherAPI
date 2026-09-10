package com.weatherapi.forecast.presentation.formatter

import com.weatherapi.forecast.domain.model.City
import com.weatherapi.forecast.domain.model.CityForecast
import com.weatherapi.forecast.domain.model.DateForecast
import com.weatherapi.forecast.domain.model.Humidity
import com.weatherapi.forecast.domain.model.Temperature
import com.weatherapi.forecast.domain.model.Wind
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FormatForecastTableUseCaseTest {

    private val useCase = FormatForecastTableUseCase()

    @Test
    fun `execute accurately retrieves local next-day forecast per city across midnight timezones`() {
        // City 1 (Kyiv, UTC+3, local date Sep 10) -> next day is Sep 11
        val city1 = CityForecast(
            city = City("Kyiv"),
            localDate = LocalDate.of(2026, 9, 10),
            forecasts = listOf(
                createSampleForecast("2026-09-10"),
                createSampleForecast("2026-09-11")
            )
        )

        // City 2 (Madrid, UTC+2, local date Sep 9) -> next day is Sep 10
        val city2 = CityForecast(
            city = City("Madrid"),
            localDate = LocalDate.of(2026, 9, 9),
            forecasts = listOf(
                createSampleForecast("2026-09-09"),
                createSampleForecast("2026-09-10")
            )
        )

        val tableOutput = useCase.execute(listOf(city1, city2))

        // Table must contain both dates as columns
        assertTrue(tableOutput.contains("2026-09-10"))
        assertTrue(tableOutput.contains("2026-09-11"))
        // Both cities present
        assertTrue(tableOutput.contains("Kyiv"))
        assertTrue(tableOutput.contains("Madrid"))
    }

    @Test
    fun `execute respects explicit targetDate parameter across all cities`() {
        val city1 = CityForecast(
            city = City("Kyiv"),
            localDate = LocalDate.of(2026, 9, 10),
            forecasts = listOf(
                createSampleForecast("2026-09-10"),
                createSampleForecast("2026-09-11")
            )
        )
        val city2 = CityForecast(
            city = City("Madrid"),
            localDate = LocalDate.of(2026, 9, 9),
            forecasts = listOf(
                createSampleForecast("2026-09-10"),
                createSampleForecast("2026-09-11")
            )
        )

        val tableOutput = useCase.execute(listOf(city1, city2), targetDate = "2026-09-11")

        assertTrue(tableOutput.contains("2026-09-11"))
        assertFalse(tableOutput.contains("2026-09-10"))
    }

    @Test
    fun `execute does not fall back to today when next-day forecast is missing`() {
        val cityWithOnlyToday = CityForecast(
            city = City("Kyiv"),
            localDate = LocalDate.of(2026, 9, 10),
            forecasts = listOf(
                createSampleForecast("2026-09-10") // only today, no tomorrow
            )
        )

        val tableOutput = useCase.execute(listOf(cityWithOnlyToday))

        // Should NOT show today's date "2026-09-10" as tomorrow's forecast
        assertFalse(tableOutput.contains("2026-09-10"), "Must not display today's forecast when next day is missing")
        org.junit.jupiter.api.Assertions.assertEquals("No weather forecast data available to display.", tableOutput)
    }

    private fun createSampleForecast(date: String): DateForecast {
        return DateForecast(
            date = date,
            temperature = Temperature(15.0, 25.0),
            humidity = Humidity(50),
            wind = Wind(15.0, "N")
        )
    }
}
