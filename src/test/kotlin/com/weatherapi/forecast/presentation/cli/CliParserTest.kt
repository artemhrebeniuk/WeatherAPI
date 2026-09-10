package com.weatherapi.forecast.presentation.cli

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CliParserTest {

    @Test
    fun `parse correctly extracts apiKey, cities, and days from valid arguments`() {
        val args = arrayOf(
            "-k", "my_api_key_123",
            "-c", "London,Paris,Berlin",
            "-d", "3"
        )

        val parsed = CliParser.parse(args)

        assertEquals("my_api_key_123", parsed.apiKey)
        assertEquals(listOf("London", "Paris", "Berlin"), parsed.cities)
        assertEquals(3, parsed.days)
        assertNull(parsed.parsingError)
    }

    @Test
    fun `parse flags errors on invalid days parameter`() {
        val args = arrayOf("--days=-5")
        val parsed = CliParser.parse(args)

        val error = requireNotNull(parsed.parsingError)
        assertTrue(error.contains("Invalid --days value"))
    }

    @Test
    fun `parse falls back to null when empty string is passed for cities`() {
        val args = arrayOf("--cities=")
        val parsed = CliParser.parse(args)

        assertNull(parsed.cities)
    }

    @Test
    fun `parse rejects days less than 2`() {
        val args = arrayOf("--days=1")
        val parsed = CliParser.parse(args)

        val error = requireNotNull(parsed.parsingError)
        assertTrue(error.contains("minimum 2 days required"))
    }

    @Test
    fun `parse flags unrecognized CLI options`() {
        val args = arrayOf("--unknown-opt")
        val parsed = CliParser.parse(args)

        val error = requireNotNull(parsed.parsingError)
        assertTrue(error.contains("Unrecognized option '--unknown-opt'"))
    }

    @Test
    fun `parse aggregates multiple errors together`() {
        val args = arrayOf("--days=abc", "--unknown-opt")
        val parsed = CliParser.parse(args)

        val error = requireNotNull(parsed.parsingError)
        assertTrue(error.contains("Invalid --days value 'abc'"))
        assertTrue(error.contains("Unrecognized option '--unknown-opt'"))
    }

    @Test
    fun `parse detects help flag`() {
        val args = arrayOf("--help")
        val parsed = CliParser.parse(args)

        assertTrue(parsed.isHelpRequested)
    }

    @Test
    fun `parse flags unexpected positional arguments`() {
        val args = arrayOf("unexpected_positional_arg")
        val parsed = CliParser.parse(args)

        val error = requireNotNull(parsed.parsingError)
        assertTrue(error.contains("Unexpected positional argument 'unexpected_positional_arg'"))
    }

    @Test
    fun `parse correctly extracts valid date parameter`() {
        val args = arrayOf("--date", "2026-09-12")
        val parsed = CliParser.parse(args)

        assertEquals("2026-09-12", parsed.targetDate)
        assertNull(parsed.parsingError)
    }

    @Test
    fun `parse correctly extracts valid inline date parameter`() {
        val args = arrayOf("--date=2026-09-15")
        val parsed = CliParser.parse(args)

        assertEquals("2026-09-15", parsed.targetDate)
        assertNull(parsed.parsingError)
    }

    @Test
    fun `parse flags invalid date format`() {
        val args = arrayOf("--date=invalid-date")
        val parsed = CliParser.parse(args)

        val error = requireNotNull(parsed.parsingError)
        assertTrue(error.contains("Invalid --date format 'invalid-date'"))
    }

    @Test
    fun `parse flags missing date value`() {
        val args = arrayOf("--date")
        val parsed = CliParser.parse(args)

        val error = requireNotNull(parsed.parsingError)
        assertTrue(error.contains("Missing value for --date argument"))
    }

    @Test
    fun `parse correctly extracts verbose flag`() {
        val argsShort = arrayOf("-v")
        val parsedShort = CliParser.parse(argsShort)
        assertTrue(parsedShort.isVerbose)

        val argsLong = arrayOf("--verbose")
        val parsedLong = CliParser.parse(argsLong)
        assertTrue(parsedLong.isVerbose)
    }
}
