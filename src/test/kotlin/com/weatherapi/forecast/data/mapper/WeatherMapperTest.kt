package com.weatherapi.forecast.data.mapper

import com.weatherapi.forecast.data.remote.dto.DayDto
import com.weatherapi.forecast.data.remote.dto.ForecastContainerDto
import com.weatherapi.forecast.data.remote.dto.ForecastDayDto
import com.weatherapi.forecast.data.remote.dto.ForecastResponseDto
import com.weatherapi.forecast.data.remote.dto.HourDto
import com.weatherapi.forecast.data.remote.dto.LocationDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class WeatherMapperTest {

    @Test
    fun `toDomain maps full ForecastResponseDto accurately into CityForecast with all 5 required metrics`() {
        val dto = ForecastResponseDto(
            location = LocationDto(
                name = "Chisinau",
                region = "Chisinau",
                country = "Moldova",
                localtime = "2026-09-09 23:00"
            ),
            forecast = ForecastContainerDto(
                forecastday = listOf(
                    ForecastDayDto(
                        date = "2026-09-09",
                        day = DayDto(
                            maxtempC = 25.0,
                            mintempC = 14.0,
                            avghumidity = 55.0,
                            maxwindKph = 15.2
                        ),
                        hour = listOf(
                            HourDto(windKph = 10.0, windDegree = 45, windDir = "NE", humidity = 60)
                        )
                    ),
                    ForecastDayDto(
                        date = "2026-09-10",
                        day = DayDto(
                            maxtempC = 27.5,
                            mintempC = 16.0,
                            avghumidity = 62.0,
                            maxwindKph = 18.4
                        ),
                        hour = listOf(
                            HourDto(windKph = 12.0, windDegree = 225, windDir = "SW", humidity = 65),
                            HourDto(windKph = 15.0, windDegree = 230, windDir = "SW", humidity = 60)
                        )
                    )
                )
            )
        )

        val cityForecast = WeatherMapper.toDomain("Chisinau", dto)

        assertEquals("Chisinau", cityForecast.city.name)
        assertEquals(LocalDate.of(2026, 9, 9), cityForecast.localDate)
        assertEquals(2, cityForecast.forecasts.size)

        val nextDay = cityForecast.nextDayForecast
        assertNotNull(nextDay)
        assertEquals("2026-09-10", nextDay!!.date)

        // 1. Min Temp (°C)
        assertEquals(16.0, nextDay.temperature.minCelsius)
        // 2. Max Temp (°C)
        assertEquals(27.5, nextDay.temperature.maxCelsius)
        // 3. Humidity (%)
        assertEquals(62, nextDay.humidity.percentage)
        // 4. Wind Speed (kph)
        assertEquals(18.4, nextDay.wind.maxSpeedKph)
        // 5. Wind Direction
        assertEquals("SW", nextDay.wind.prevailingDirection)
    }

    @Test
    fun `toDomain safely sanitizes NaN values and negative wind speed without throwing exceptions`() {
        val dto = ForecastResponseDto(
            location = LocationDto(name = "Kyiv"),
            forecast = ForecastContainerDto(
                forecastday = listOf(
                    ForecastDayDto(
                        date = "2026-09-10",
                        day = DayDto(
                            maxtempC = Double.NaN,
                            mintempC = Double.NaN,
                            avghumidity = Double.NaN,
                            maxwindKph = -15.0 // negative wind speed
                        ),
                        hour = listOf(
                            HourDto(windKph = Double.NaN, windDegree = null, windDir = "N")
                        )
                    )
                )
            )
        )

        val cityForecast = WeatherMapper.toDomain("Kyiv", dto)
        val forecast = cityForecast.forecasts.first()

        assertEquals(0.0, forecast.temperature.minCelsius)
        assertEquals(0.0, forecast.temperature.maxCelsius)
        assertEquals(0, forecast.humidity.percentage)
        assertEquals(0.0, forecast.wind.maxSpeedKph)
        assertEquals("N", forecast.wind.prevailingDirection)
    }

    @Test
    fun `toDomain preserves negative temperatures symmetrically when mintempC or maxtempC is null`() {
        val dto = ForecastResponseDto(
            location = LocationDto(name = "Kyiv"),
            forecast = ForecastContainerDto(
                forecastday = listOf(
                    ForecastDayDto(
                        date = "2026-01-15",
                        day = DayDto(
                            maxtempC = -12.0,
                            mintempC = null,
                            avghumidity = 80.0,
                            maxwindKph = 20.0
                        ),
                        hour = emptyList()
                    )
                )
            )
        )

        val cityForecast = WeatherMapper.toDomain("Kyiv", dto)
        val forecast = cityForecast.forecasts.first()

        assertEquals(-12.0, forecast.temperature.minCelsius)
        assertEquals(-12.0, forecast.temperature.maxCelsius)
    }

    @Test
    fun `toDomain falls back to requestedCity when LocationDto name is null or blank`() {
        val dto = ForecastResponseDto(
            location = LocationDto(name = null),
            forecast = ForecastContainerDto(forecastday = emptyList())
        )

        val cityForecast = WeatherMapper.toDomain("Amsterdam", dto)
        assertEquals("Amsterdam", cityForecast.city.name)
    }

    @Test
    fun `toDomain safely handles null day object and skips null date without throwing exceptions`() {
        val dto = ForecastResponseDto(
            location = LocationDto(name = "Madrid"),
            forecast = ForecastContainerDto(
                forecastday = listOf(
                    ForecastDayDto(
                        date = "2026-09-12",
                        day = null,
                        hour = emptyList()
                    ),
                    ForecastDayDto(
                        date = null,
                        day = DayDto(),
                        hour = emptyList()
                    )
                )
            )
        )

        val cityForecast = WeatherMapper.toDomain("Madrid", dto)
        assertEquals(1, cityForecast.forecasts.size)
        val forecast = cityForecast.forecasts.first()
        assertEquals("2026-09-12", forecast.date)
        assertEquals(0.0, forecast.temperature.minCelsius)
        assertEquals(0.0, forecast.wind.maxSpeedKph)
    }
}
