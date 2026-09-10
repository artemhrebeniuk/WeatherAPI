package com.weatherapi.forecast.domain.service

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Domain service responsible for determining the prevailing wind direction
 * from hourly meteorological data.
 *
 * Meteorological insight:
 * WeatherAPI provides 'wind_dir' (16-point compass) and 'wind_degree' (0-360°)
 * inside hourly forecasts, but does not provide an aggregated daily wind direction
 * in the top-level 'day' object. This service employs vector averaging and
 * frequency distribution analysis to accurately synthesize the daily prevailing wind direction.
 */
object WindDirectionCalculator {

    private val COMPASS_POINTS = listOf(
        "N", "NNE", "NE", "ENE",
        "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW",
        "W", "WNW", "NW", "NNW"
    )

    private const val SECTOR_DEGREES = 360.0 / 16.0 // 22.5 degrees
    private const val EPSILON = 1e-5

    /**
     * Minimal representation of hourly wind data needed for calculation.
     */
    data class HourlyWind(
        val windKph: Double? = null,
        val windDegree: Int? = null,
        val windDir: String? = null
    )

    /**
     * Computes the prevailing wind direction from a collection of hourly measurements.
     *
     * Priority:
     * 1. Direct explicit day direction if already provided.
     * 2. Vector average of valid 'windDegree' values (weighted by wind speed if present).
     * 3. Statistical mode (most frequent) of 'windDir' strings.
     * 4. "N/A" fallback if no data is available.
     */
    fun calculatePrevailingDirection(
        explicitDayDirection: String?,
        hourlyWinds: List<HourlyWind>
    ): String {
        if (!explicitDayDirection.isNullOrBlank()) {
            return explicitDayDirection.trim()
        }

        if (hourlyWinds.isEmpty()) {
            return "N/A"
        }

        // Strictly validate meteorological azimuth: 0 <= degree <= 360 and valid finite non-negative speed
        val validDegreeWinds = hourlyWinds.filter {
            it.windDegree != null &&
                it.windDegree in 0..360 &&
                (it.windKph == null || (it.windKph.isFinite() && it.windKph >= 0.0))
        }

        if (validDegreeWinds.isNotEmpty()) {
            val avgDegree = computeVectorAverageDegree(validDegreeWinds)
            if (avgDegree != null) {
                return degreeToCompass(avgDegree)
            }
        }

        // Fallback: Statistical mode of windDir compass strings with deterministic tie-breaking
        val validDirections = hourlyWinds.mapNotNull { it.windDir?.trim()?.takeIf { d -> d.isNotBlank() } }
        if (validDirections.isNotEmpty()) {
            val frequencyMap = validDirections.groupingBy { it }.eachCount()
            val maxFreq = frequencyMap.values.maxOrNull() ?: 0
            val mostFrequent = frequencyMap.entries
                .filter { it.value == maxFreq }
                .minByOrNull { it.key } // deterministic alphabetical tie-break
                ?.key
            if (mostFrequent != null) {
                return mostFrequent
            }
        }

        return "N/A"
    }

    /**
     * Converts an azimuth degree (0° - 360°) to the closest 16-point compass heading.
     */
    fun degreeToCompass(degree: Double): String {
        if (!degree.isFinite()) return "N/A"
        val normalized = ((degree % 360.0) + 360.0) % 360.0
        val index = (((normalized + (SECTOR_DEGREES / 2.0)) / SECTOR_DEGREES).toInt()) % 16
        return COMPASS_POINTS[index]
    }

    /**
     * Computes circular mean angle using meteorological vector decomposition.
     *
     * In navigational coordinates, compass azimuth theta is measured clockwise from North (0°):
     * - North projection: v = cos(theta) * weight
     * - East projection:  u = sin(theta) * weight
     *
     * The mean azimuth is resolved via atan2(u, v) = atan2(sumY, sumX) in degrees [0, 360).
     */
    private fun computeVectorAverageDegree(winds: List<HourlyWind>): Double? {
        var sumX = 0.0
        var sumY = 0.0
        var count = 0

        for ((windKph, windDegree) in winds) {
            val degree = windDegree ?: continue
            val normalizedDegree = ((degree.toDouble() % 360.0) + 360.0) % 360.0
            val radians = Math.toRadians(normalizedDegree)
            val weight = (windKph ?: 1.0).coerceAtLeast(0.1)

            sumX += cos(radians) * weight
            sumY += sin(radians) * weight
            count++
        }

        if (count == 0 || !sumX.isFinite() || !sumY.isFinite() || hypot(sumX, sumY) < EPSILON) {
            return null
        }

        val angleRadians = atan2(sumY, sumX)
        val angleDegrees = Math.toDegrees(angleRadians)
        return ((angleDegrees % 360.0) + 360.0) % 360.0
    }
}
