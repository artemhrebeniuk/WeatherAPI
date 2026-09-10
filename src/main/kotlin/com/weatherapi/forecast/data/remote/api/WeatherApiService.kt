package com.weatherapi.forecast.data.remote.api

import com.weatherapi.forecast.data.remote.dto.ForecastResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit 2 declarative HTTP client definition for WeatherAPI.com endpoints.
 */
interface WeatherApiService {

    /**
     * Retrieves weather forecast data including daily summaries and hourly intervals.
     *
     * @param query Location query: city name, latitude/longitude coordinates, US zip, UK postcode, etc.
     * @param days Number of forecast days to retrieve (1 to 14).
     * @param aqi Whether to include air quality data ("yes" or "no").
     * @param alerts Whether to include weather alerts ("yes" or "no").
     * @return [ForecastResponseDto] deserialized response payload.
     */
    @GET("forecast.json")
    suspend fun getForecast(
        @Query("q") query: String,
        @Query("days") days: Int = 3,
        @Query("aqi") aqi: String = "no",
        @Query("alerts") alerts: String = "no"
    ): ForecastResponseDto
}
