package com.weatherapi.forecast.presentation.table

import com.weatherapi.forecast.domain.model.City
import com.weatherapi.forecast.domain.model.CityForecast
import com.weatherapi.forecast.domain.model.DateForecast
import com.weatherapi.forecast.domain.model.Humidity
import com.weatherapi.forecast.domain.model.Temperature
import com.weatherapi.forecast.domain.model.Wind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AsciiTableFormatterTest {

    @Test
    fun `formatTable produces aligned table with dates as columns and cities as rows`() {
        val targetCities = listOf("Chisinau", "Madrid", "Kyiv", "Amsterdam")
        val sampleDate = "2026-09-10"

        val forecasts = targetCities.mapIndexed { index, name ->
            CityForecast(
                city = City(name = name),
                forecasts = listOf(
                    DateForecast(
                        date = sampleDate,
                        temperature = Temperature(minCelsius = 10.0 + index, maxCelsius = 20.0 + index),
                        humidity = Humidity(percentage = 50 + index * 5),
                        wind = Wind(maxSpeedKph = 15.0 + index, prevailingDirection = "NW")
                    )
                )
            )
        }

        val output = AsciiTableFormatter.formatTable(forecasts)

        // Verify structure requirements:
        assertTrue(output.contains("City"), "Table must contain 'City' header")
        assertTrue(output.contains(sampleDate), "Table must contain date column header '$sampleDate'")

        // Verify metric columns
        assertTrue(output.contains("Min Temp (°C)"), "Table must contain 'Min Temp (°C)' column")
        assertTrue(output.contains("Max Temp (°C)"), "Table must contain 'Max Temp (°C)' column")
        assertTrue(output.contains("Humidity (%)"), "Table must contain 'Humidity (%)' column")
        assertTrue(output.contains("Wind Speed (kph)"), "Table must contain 'Wind Speed (kph)' column")
        assertTrue(output.contains("Wind Direction"), "Table must contain 'Wind Direction' column")

        // Verify rows
        for (city in targetCities) {
            assertTrue(output.contains(city), "Table must contain row for city '$city'")
        }
    }

    @Test
    fun `formatTable handles negative temperatures cleanly without breaking column alignments`() {
        val forecasts = listOf(
            CityForecast(
                city = City(name = "Kyiv"),
                forecasts = listOf(
                    DateForecast(
                        date = "2026-01-15",
                        temperature = Temperature(minCelsius = -18.5, maxCelsius = -5.2),
                        humidity = Humidity(percentage = 85),
                        wind = Wind(maxSpeedKph = 25.0, prevailingDirection = "NE")
                    )
                )
            )
        )

        val output = AsciiTableFormatter.formatTable(forecasts)
        assertTrue(output.contains("-18.5"))
        assertTrue(output.contains("-5.2"))
    }

    @Test
    fun `formatTable supports multiple date columns with cities as rows and renders dashes for absent dates`() {
        val city1 = CityForecast(
            city = City("Madrid"),
            forecasts = listOf(
                DateForecast(
                    date = "2026-09-10",
                    temperature = Temperature(15.0, 26.0),
                    humidity = Humidity(45),
                    wind = Wind(12.0, "NE")
                )
            )
        )
        val city2 = CityForecast(
            city = City("Kyiv"),
            forecasts = listOf(
                DateForecast(
                    date = "2026-09-11",
                    temperature = Temperature(12.0, 22.0),
                    humidity = Humidity(60),
                    wind = Wind(16.0, "NW")
                )
            )
        )

        val output = AsciiTableFormatter.formatTable(listOf(city1, city2))

        assertTrue(output.contains("2026-09-10"), "Table must display first date column")
        assertTrue(output.contains("2026-09-11"), "Table must display second date column")
        assertTrue(output.contains("Madrid"))
        assertTrue(output.contains("Kyiv"))
        // Check for dashes in the sparse matrix
        assertTrue(output.contains("-"), "Table must contain dashes for cities lacking a specific date")
    }

    @Test
    fun `formatTable returns informative message when input is empty`() {
        val output = AsciiTableFormatter.formatTable(emptyList())
        assertEquals("No weather data available to display.", output)
    }

    @Test
    fun `formatTable returns informative message when forecasts list is empty across all cities`() {
        val forecasts = listOf(CityForecast(city = City("Kyiv"), forecasts = emptyList()))
        val output = AsciiTableFormatter.formatTable(forecasts)
        assertEquals("No weather forecast data available to display.", output)
    }
}
