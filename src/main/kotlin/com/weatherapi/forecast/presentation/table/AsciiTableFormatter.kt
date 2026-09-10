package com.weatherapi.forecast.presentation.table

import com.weatherapi.forecast.domain.model.CityForecast
import com.weatherapi.forecast.domain.model.DateForecast
import java.util.Locale

/**
 * High-performance, zero-dependency ASCII table formatter designed
 * to display weather forecast data with Dates as columns and Cities as rows.
 */
object AsciiTableFormatter {

    private const val COL_CITY = "City"
    private const val COL_MIN_TEMP = "Min Temp (°C)"
    private const val COL_MAX_TEMP = "Max Temp (°C)"
    private const val COL_HUMIDITY = "Humidity (%)"
    private const val COL_WIND_SPEED = "Wind Speed (kph)"
    private const val COL_WIND_DIR = "Wind Direction"

    private val METRIC_HEADERS = listOf(
        COL_MIN_TEMP,
        COL_MAX_TEMP,
        COL_HUMIDITY,
        COL_WIND_SPEED,
        COL_WIND_DIR
    )

    /**
     * Formats a list of [CityForecast] objects into a beautiful ASCII table
     * adhering strictly to the challenge requirements:
     * - Dates as columns
     * - Cities as rows
     * - All 5 data points displayed for each location
     */
    fun formatTable(cityForecasts: List<CityForecast>): String {
        if (cityForecasts.isEmpty()) {
            return "No weather data available to display."
        }

        // Determine all unique dates across all cities (sorted chronologically)
        val allDates = cityForecasts
            .flatMap { it.forecasts.map { f -> f.date } }
            .distinct()
            .sorted()

        if (allDates.isEmpty()) {
            return "No weather forecast data available to display."
        }

        val metricHeaders = METRIC_HEADERS

        // Calculate maximum column widths for proper alignment
        val cityColumnWidth = maxOf(
            COL_CITY.length,
            cityForecasts.maxOfOrNull { it.city.name.length } ?: 0
        ) + 2 // padding

        // Map: Date -> List of 5 column widths
        val dateMetricWidths = mutableMapOf<String, MutableList<Int>>()
        for (date in allDates) {
            val widths = metricHeaders.map { it.length }.toMutableList()

            for ((_, forecasts) in cityForecasts) {
                val forecast = forecasts.find { it.date == date }
                if (forecast != null) {
                    val valMinTemp = formatDouble(forecast.temperature.minCelsius)
                    val valMaxTemp = formatDouble(forecast.temperature.maxCelsius)
                    val valHumidity = "${forecast.humidity.percentage}%"
                    val valWindSpeed = formatDouble(forecast.wind.maxSpeedKph)
                    val valWindDir = forecast.wind.prevailingDirection

                    widths[0] = maxOf(widths[0], valMinTemp.length)
                    widths[1] = maxOf(widths[1], valMaxTemp.length)
                    widths[2] = maxOf(widths[2], valHumidity.length)
                    widths[3] = maxOf(widths[3], valWindSpeed.length)
                    widths[4] = maxOf(widths[4], valWindDir.length)
                }
            }
            // Add padding
            dateMetricWidths[date] = widths.map { it + 2 }.toMutableList()
        }

        val sb = StringBuilder()

        // 1. Build Top Border (aligned with Date Tier span)
        val topBorder = buildDateTierBorder(cityColumnWidth, allDates, dateMetricWidths)
        sb.appendLine(topBorder)

        // 2. Build Date Header Row (Tier 1: City | Date1 | Date2 ...)
        sb.append("|")
        sb.append(center(COL_CITY, cityColumnWidth))
        for (date in allDates) {
            sb.append("|")
            val totalDateSpanWidth = dateMetricWidths[date]!!.sum() + (metricHeaders.size - 1)
            sb.append(center(date, totalDateSpanWidth))
        }
        sb.appendLine("|")

        // 3. Sub-header separator (full grid with sub-columns)
        val gridBorder = buildGridBorder(cityColumnWidth, allDates, dateMetricWidths)
        sb.appendLine(gridBorder)

        // 4. Build Metric Sub-header Row (Tier 2: | Min Temp | Max Temp | Humidity | Wind Speed | Wind Dir |)
        sb.append("|")
        sb.append(center("", cityColumnWidth))
        for (date in allDates) {
            val widths = dateMetricWidths[date]!!
            for ((idx, header) in metricHeaders.withIndex()) {
                sb.append("|")
                sb.append(center(header, widths[idx]))
            }
        }
        sb.appendLine("|")

        // 5. Header / Body divider
        sb.appendLine(gridBorder)

        // 6. Data Rows (Cities)
        for ((city, forecasts) in cityForecasts) {
            sb.append("|")
            sb.append(leftAlign(" " + city.name, cityColumnWidth))

            for (date in allDates) {
                val widths = dateMetricWidths[date]!!
                val forecast = forecasts.find { it.date == date }

                if (forecast != null) {
                    val valMinTemp = formatDouble(forecast.temperature.minCelsius)
                    val valMaxTemp = formatDouble(forecast.temperature.maxCelsius)
                    val valHumidity = "${forecast.humidity.percentage}%"
                    val valWindSpeed = formatDouble(forecast.wind.maxSpeedKph)
                    val valWindDir = forecast.wind.prevailingDirection

                    sb.append("|").append(center(valMinTemp, widths[0]))
                    sb.append("|").append(center(valMaxTemp, widths[1]))
                    sb.append("|").append(center(valHumidity, widths[2]))
                    sb.append("|").append(center(valWindSpeed, widths[3]))
                    sb.append("|").append(center(valWindDir, widths[4]))
                } else {
                    for (w in widths) {
                        sb.append("|").append(center("-", w))
                    }
                }
            }
            sb.appendLine("|")
        }

        // 7. Bottom Border
        sb.append(gridBorder)

        return sb.toString()
    }

    private fun buildDateTierBorder(
        cityWidth: Int,
        dates: List<String>,
        dateMetricWidths: Map<String, List<Int>>
    ): String {
        val sb = StringBuilder()
        sb.append("+")
        sb.append("-".repeat(cityWidth))
        for (date in dates) {
            val totalDateSpanWidth = dateMetricWidths[date]!!.sum() + (METRIC_HEADERS.size - 1)
            sb.append("+")
            sb.append("-".repeat(totalDateSpanWidth))
        }
        sb.append("+")
        return sb.toString()
    }

    private fun buildGridBorder(
        cityWidth: Int,
        dates: List<String>,
        dateMetricWidths: Map<String, List<Int>>
    ): String {
        val sb = StringBuilder()
        sb.append("+")
        sb.append("-".repeat(cityWidth))
        for (date in dates) {
            val widths = dateMetricWidths[date]!!
            for (w in widths) {
                sb.append("+")
                sb.append("-".repeat(w))
            }
        }
        sb.append("+")
        return sb.toString()
    }

    private fun center(text: String, width: Int): String {
        if (text.length >= width) return text
        val totalPadding = width - text.length
        val padLeft = totalPadding / 2
        val padRight = totalPadding - padLeft
        return " ".repeat(padLeft) + text + " ".repeat(padRight)
    }

    private fun leftAlign(text: String, width: Int): String {
        if (text.length >= width) return text
        return text + " ".repeat(width - text.length)
    }

    private fun formatDouble(value: Double): String {
        return String.format(Locale.US, "%.1f", value)
    }
}
