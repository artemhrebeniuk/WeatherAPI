package com.weatherapi.forecast.domain.model

import java.time.LocalDate

/**
 * Domain entity representing a geographic location.
 */
data class City(
    val name: String,
    val region: String? = null,
    val country: String? = null
)

/**
 * Value object representing temperature metrics in Celsius.
 */
data class Temperature(
    val minCelsius: Double,
    val maxCelsius: Double
) {
    init {
        require(minCelsius.isFinite() && maxCelsius.isFinite()) {
            "Temperature values must be finite numbers"
        }
        require(minCelsius <= maxCelsius) {
            "Minimum temperature ($minCelsius°C) cannot exceed maximum temperature ($maxCelsius°C)"
        }
    }
}

/**
 * Value object representing relative humidity percentage.
 */
data class Humidity(
    val percentage: Int
) {
    init {
        require(percentage in 0..100) {
            "Humidity percentage must be within [0, 100], got: $percentage"
        }
    }
}

/**
 * Value object representing wind metrics (speed in kph and prevailing direction).
 */
data class Wind(
    val maxSpeedKph: Double,
    val prevailingDirection: String
) {
    init {
        require(maxSpeedKph.isFinite() && maxSpeedKph >= 0.0) {
            "Wind speed must be non-negative and finite, got: $maxSpeedKph"
        }
        require(prevailingDirection.isNotBlank()) {
            "Wind direction must not be blank"
        }
    }
}

/**
 * Aggregate representing the forecast for a single calendar date.
 */
data class DateForecast(
    val date: String,
    val temperature: Temperature,
    val humidity: Humidity,
    val wind: Wind
)

/**
 * Aggregate root representing the weather forecast for a city across one or more dates.
 */
data class CityForecast(
    val city: City,
    val forecasts: List<DateForecast>,
    val localDate: LocalDate? = null
) {
    /**
     * Retrieves the forecast for the next day relative to the city's local date,
     * or the day after the first available forecast date.
     */
    val nextDayForecast: DateForecast?
        get() {
            if (forecasts.isEmpty()) return null
            val baseDate = localDate ?: runCatching { LocalDate.parse(forecasts.first().date) }.getOrNull()
            return if (baseDate != null) {
                val targetTomorrow = baseDate.plusDays(1).toString()
                // Strict resolution: if tomorrow is known but missing, return null rather than an arbitrary day
                forecasts.find { it.date == targetTomorrow }
            } else {
                // Fallback to index 1 only if base date could not be parsed at all
                forecasts.getOrNull(1)
            }
        }
}
