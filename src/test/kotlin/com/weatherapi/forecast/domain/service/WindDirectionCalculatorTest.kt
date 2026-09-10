package com.weatherapi.forecast.domain.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class WindDirectionCalculatorTest {

    @ParameterizedTest(name = "Degree {0}° should map to compass {1}")
    @CsvSource(
        "0, N",
        "10, N",
        "355, N",
        "22.5, NNE",
        "45, NE",
        "67.5, ENE",
        "90, E",
        "112.5, ESE",
        "135, SE",
        "157.5, SSE",
        "180, S",
        "202.5, SSW",
        "225, SW",
        "247.5, WSW",
        "270, W",
        "292.5, WNW",
        "315, NW",
        "337.5, NNW",
        "360, N"
    )
    fun `degreeToCompass maps degrees accurately across all 16 sectors`(degree: Double, expectedCompass: String) {
        val actual = WindDirectionCalculator.degreeToCompass(degree)
        assertEquals(expectedCompass, actual)
    }

    @Test
    fun `calculatePrevailingDirection handles circular boundary across North 0-360 degrees`() {
        // Two measurements: one at 350° (North) and one at 10° (North)
        // Standard arithmetic average would incorrectly be (350+10)/2 = 180° (South)
        // Vector averaging should correctly produce ~0° (North)
        val hourlyWinds = listOf(
            WindDirectionCalculator.HourlyWind(windKph = 10.0, windDegree = 350),
            WindDirectionCalculator.HourlyWind(windKph = 10.0, windDegree = 10)
        )

        val result = WindDirectionCalculator.calculatePrevailingDirection(
            explicitDayDirection = null,
            hourlyWinds = hourlyWinds
        )

        assertEquals("N", result)
    }

    @Test
    fun `calculatePrevailingDirection respects explicit day direction when provided`() {
        val hourlyWinds = listOf(
            WindDirectionCalculator.HourlyWind(windKph = 15.0, windDegree = 180)
        )

        val result = WindDirectionCalculator.calculatePrevailingDirection(
            explicitDayDirection = "NW",
            hourlyWinds = hourlyWinds
        )

        assertEquals("NW", result)
    }

    @Test
    fun `calculatePrevailingDirection falls back to mode of windDir when degrees are missing`() {
        val hourlyWinds = listOf(
            WindDirectionCalculator.HourlyWind(windKph = 12.0, windDir = "SW"),
            WindDirectionCalculator.HourlyWind(windKph = 14.0, windDir = "SW"),
            WindDirectionCalculator.HourlyWind(windKph = 10.0, windDir = "ENE"),
            WindDirectionCalculator.HourlyWind(windKph = 11.0, windDir = "SW")
        )

        val result = WindDirectionCalculator.calculatePrevailingDirection(
            explicitDayDirection = null,
            hourlyWinds = hourlyWinds
        )

        assertEquals("SW", result)
    }

    @Test
    fun `calculatePrevailingDirection returns NA when hourly data is empty`() {
        val result = WindDirectionCalculator.calculatePrevailingDirection(
            explicitDayDirection = null,
            hourlyWinds = emptyList()
        )

        assertEquals("N/A", result)
    }

    @Test
    fun `calculatePrevailingDirection handles opposing winds singularity by falling back to compass mode`() {
        // Equal wind speed at 0° (North) and 180° (South) -> vector sum magnitude is below EPSILON
        // Plus an additional 'S' reading without valid degree so that mode deterministically favors 'S'
        val hourlyWinds = listOf(
            WindDirectionCalculator.HourlyWind(windKph = 10.0, windDegree = 0, windDir = "N"),
            WindDirectionCalculator.HourlyWind(windKph = 10.0, windDegree = 180, windDir = "S"),
            WindDirectionCalculator.HourlyWind(windKph = null, windDegree = null, windDir = "S")
        )

        val result = WindDirectionCalculator.calculatePrevailingDirection(
            explicitDayDirection = null,
            hourlyWinds = hourlyWinds
        )

        // Vector average is degenerate (< EPSILON), falls back to mode of windDir ('S')
        assertEquals("S", result)
    }

    @Test
    fun `calculatePrevailingDirection handles IEEE 754 floating point cancellation on East and West opposing winds`() {
        // In IEEE 754, cos(90°) + cos(270°) produces ~ -1.22e-15, which is not exactly 0.0.
        // EPSILON threshold ensures this is correctly identified as a singularity.
        val hourlyWinds = listOf(
            WindDirectionCalculator.HourlyWind(windKph = 10.0, windDegree = 90, windDir = "E"),
            WindDirectionCalculator.HourlyWind(windKph = 10.0, windDegree = 270, windDir = "W")
        )

        val result = WindDirectionCalculator.calculatePrevailingDirection(
            explicitDayDirection = null,
            hourlyWinds = hourlyWinds
        )

        // Falls back to mode of ["E", "W"], deterministic alphabetical tie-break picks "E"
        assertEquals("E", result)
    }

    @Test
    fun `calculatePrevailingDirection ignores non-finite wind speed and out-of-range degrees`() {
        val hourlyWinds = listOf(
            WindDirectionCalculator.HourlyWind(windKph = Double.POSITIVE_INFINITY, windDegree = 90),
            // Out-of-range degree with higher weight must be rejected, not normalized
            WindDirectionCalculator.HourlyWind(windKph = 50.0, windDegree = 450),
            WindDirectionCalculator.HourlyWind(windKph = 50.0, windDegree = -30),
            WindDirectionCalculator.HourlyWind(windKph = 10.0, windDegree = 270) // valid West
        )

        val result = WindDirectionCalculator.calculatePrevailingDirection(
            explicitDayDirection = null,
            hourlyWinds = hourlyWinds
        )

        assertEquals("W", result)
    }
}
