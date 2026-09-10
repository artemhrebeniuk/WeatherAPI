package com.weatherapi.forecast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class ApplicationTest {

    @Test
    fun `runApplication returns 0 and prints help when help requested`() {
        val outBytes = ByteArrayOutputStream()
        val errBytes = ByteArrayOutputStream()
        val stdout = PrintStream(outBytes)
        val stderr = PrintStream(errBytes)

        val code = runApplication(arrayOf("--help"), stdout, stderr)

        assertEquals(0, code)
        assertTrue(outBytes.toString().contains("USAGE:"))
        assertTrue(outBytes.toString().contains("OPTIONS:"))
    }

    @Test
    fun `runApplication returns 1 and writes to stderr on parsing error`() {
        val outBytes = ByteArrayOutputStream()
        val errBytes = ByteArrayOutputStream()
        val stdout = PrintStream(outBytes)
        val stderr = PrintStream(errBytes)

        val code = runApplication(arrayOf("--unknown-option"), stdout, stderr)

        assertEquals(1, code)
        assertTrue(errBytes.toString().contains("[ERROR] Unrecognized option '--unknown-option'"))
    }

    @Test
    fun `runApplication returns 1 when api key is missing`() {
        val outBytes = ByteArrayOutputStream()
        val errBytes = ByteArrayOutputStream()
        val stdout = PrintStream(outBytes)
        val stderr = PrintStream(errBytes)

        val code = runApplication(arrayOf("--cities=Kyiv"), stdout, stderr)

        assertEquals(1, code)
        assertTrue(errBytes.toString().contains("[ERROR] API key is missing!"))
    }

    @Test
    fun `runApplication returns 1 on invalid date argument`() {
        val outBytes = ByteArrayOutputStream()
        val errBytes = ByteArrayOutputStream()
        val stdout = PrintStream(outBytes)
        val stderr = PrintStream(errBytes)

        val code = runApplication(arrayOf("--api-key=test", "--date=invalid-date"), stdout, stderr)

        assertEquals(1, code)
        assertTrue(errBytes.toString().contains("Invalid --date format 'invalid-date'"))
    }
}
