package com.weatherapi.forecast.data.mapper

import com.weatherapi.forecast.data.remote.dto.ForecastDayDto
import com.weatherapi.forecast.data.remote.dto.ForecastResponseDto
import com.weatherapi.forecast.data.remote.dto.DayDto
import com.weatherapi.forecast.domain.model.City
import com.weatherapi.forecast.domain.model.CityForecast
import com.weatherapi.forecast.domain.model.DateForecast
import com.weatherapi.forecast.domain.model.Humidity
import com.weatherapi.forecast.domain.model.Temperature
import com.weatherapi.forecast.domain.model.Wind
import com.weatherapi.forecast.domain.service.WindDirectionCalculator
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Maps raw WeatherAPI DTOs into clean, strongly typed Domain entities.
 */
object WeatherMapper {

    /**
     * Converts a [ForecastResponseDto] into a clean [CityForecast] domain model.
     *
     * @param requestedCity The name of the city requested by the caller (used as fallback).
     * @param dto The raw API response DTO.
     * @return [CityForecast] domain model.
     */
    fun toDomain(requestedCity: String, dto: ForecastResponseDto): CityForecast {
        val city = City(
            name = dto.location?.name?.takeIf { it.isNotBlank() } ?: requestedCity,
            region = dto.location?.region,
            country = dto.location?.country
        )

        val localDate = dto.location?.localtime?.take(10)?.let { dateStr ->
            runCatching { LocalDate.parse(dateStr) }.getOrNull()
        } ?: dto.forecast?.forecastday?.firstOrNull()?.date?.take(10)?.let { dateStr ->
            runCatching { LocalDate.parse(dateStr) }.getOrNull()
        }

        val dateForecasts = dto.forecast?.forecastday?.mapNotNull { forecastDayDto ->
            mapForecastDay(forecastDayDto)
        } ?: emptyList()

        return CityForecast(
            city = city,
            forecasts = dateForecasts,
            localDate = localDate
        )
    }

    private fun mapForecastDay(dto: ForecastDayDto): DateForecast? {
        val rawDate = dto.date?.takeIf { it.isNotBlank() } ?: return null
        val parsedDate = runCatching { LocalDate.parse(rawDate) }.getOrNull() ?: return null
        val day = dto.day ?: DayDto()

        // 1. Defend symmetrically against null, non-finite, and inverted temperatures,
        // using hourly intervals as robust fallback before falling back to absolute zero.
        val hourlyTemps = dto.hour.mapNotNull { it.tempC?.takeIf { t -> t.isFinite() } }
        val hourlyMin = hourlyTemps.minOrNull()
        val hourlyMax = hourlyTemps.maxOrNull()

        val rawMin = day.mintempC?.takeIf { it.isFinite() } ?: hourlyMin
        val rawMax = day.maxtempC?.takeIf { it.isFinite() } ?: hourlyMax

        val fallbackTemp = rawMin ?: rawMax ?: 0.0
        val effectiveMin = rawMin ?: fallbackTemp
        val effectiveMax = rawMax ?: fallbackTemp
        val minTemp = minOf(effectiveMin, effectiveMax)
        val maxTemp = maxOf(effectiveMin, effectiveMax)

        // 2. Defend against non-finite values in humidity
        val humidityPercent = day.avghumidity?.takeIf { it.isFinite() }?.roundToInt()
            ?: dto.hour.mapNotNull { it.humidity }.average().takeIf { it.isFinite() }?.roundToInt()
            ?: 0
        val clampedHumidity = humidityPercent.coerceIn(0, 100)

        // 3. Defend against non-finite and negative wind speed
        val rawWind = day.maxwindKph ?: dto.hour.mapNotNull { it.windKph }.maxOrNull() ?: 0.0
        val safeWindSpeed = if (!rawWind.isFinite()) 0.0 else rawWind.coerceAtLeast(0.0)

        val hourlyWinds = dto.hour.map { hourDto ->
            WindDirectionCalculator.HourlyWind(
                windKph = hourDto.windKph?.takeIf { it.isFinite() }?.coerceAtLeast(0.0),
                windDegree = hourDto.windDegree,
                windDir = hourDto.windDir
            )
        }

        // Calculate prevailing wind direction using domain service
        val prevailingDirection = WindDirectionCalculator.calculatePrevailingDirection(
            explicitDayDirection = day.windDir,
            hourlyWinds = hourlyWinds
        )

        return DateForecast(
            date = parsedDate.toString(),
            temperature = Temperature(
                minCelsius = minTemp,
                maxCelsius = maxTemp
            ),
            humidity = Humidity(
                percentage = clampedHumidity
            ),
            wind = Wind(
                maxSpeedKph = safeWindSpeed,
                prevailingDirection = prevailingDirection
            )
        )
    }
}
