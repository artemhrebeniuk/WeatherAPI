package com.weatherapi.forecast.domain.repository

import com.weatherapi.forecast.domain.model.CityForecast

/**
 * Repository abstraction for querying weather forecast data.
 * Adheres to Dependency Inversion Principle (DIP).
 */
interface WeatherRepository {

    /**
     * Fetches the weather forecast for a specified city name or query string.
     *
     * @param city Name of the city (e.g., "Chisinau", "Madrid", "Kyiv", "Amsterdam").
     * @param days Number of forecast days to retrieve (defaults to 3 for cross-timezone coverage).
     * @return [Result] containing [CityForecast] on success, or an encapsulated exception/WeatherError on failure.
     */
    suspend fun getForecast(city: String, days: Int = DEFAULT_DAYS): Result<CityForecast>

    companion object {
        const val DEFAULT_DAYS: Int = 3
    }
}
