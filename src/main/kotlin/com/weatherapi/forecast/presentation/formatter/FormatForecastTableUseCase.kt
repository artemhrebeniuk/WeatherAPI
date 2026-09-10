package com.weatherapi.forecast.presentation.formatter

import com.weatherapi.forecast.domain.model.CityForecast
import com.weatherapi.forecast.presentation.table.AsciiTableFormatter

/**
 * Presentation-layer orchestrator for formatting retrieved weather forecasts
 * into an aligned ASCII table.
 *
 * Placed in the presentation layer to strictly adhere to Clean Architecture
 * and prevent the Domain layer from depending on UI formatting components.
 */
class FormatForecastTableUseCase {

    /**
     * Formats forecasts for display.
     *
     * For each city, selects its local next-day forecast (or matched [targetDate] if explicitly provided).
     * If cities span across midnight timezones, each city accurately displays its own next calendar day,
     * rendered as separate date columns in the multi-column ASCII table.
     *
     * @param cityForecasts List of forecasts for all queried cities.
     * @param targetDate Optional explicit target date. If null, automatically resolves
     *                   each city's respective next-day forecast.
     * @return Formatted table string ready for STDOUT.
     */
    fun execute(cityForecasts: List<CityForecast>, targetDate: String? = null): String {
        val filteredForecasts = cityForecasts.map { cf ->
            val forecastForCity = if (targetDate != null) {
                cf.forecasts.find { it.date == targetDate }
            } else {
                cf.nextDayForecast
            }

            cf.copy(forecasts = listOfNotNull(forecastForCity))
        }

        return AsciiTableFormatter.formatTable(filteredForecasts)
    }
}
