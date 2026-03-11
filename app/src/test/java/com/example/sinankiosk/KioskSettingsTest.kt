package com.example.sinankiosk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KioskSettingsTest {
    @Test
    fun normalizeDomain_addsHttpsWhenMissing() {
        assertEquals(
            "https://example.com",
            KioskSettings.normalizeDomain("example.com")
        )
    }

    @Test
    fun normalizeDomain_keepsValidHttpUrl() {
        assertEquals(
            "http://localhost:3000",
            KioskSettings.normalizeDomain("http://localhost:3000")
        )
    }

    @Test
    fun normalizeDomain_rejectsUnsupportedSchemes() {
        assertNull(KioskSettings.normalizeDomain("ftp://example.com"))
    }

    @Test
    fun isValidPin_requiresFourNumericCharacters() {
        assertTrue(KioskSettings.isValidPin("1234"))
        assertFalse(KioskSettings.isValidPin("123"))
        assertFalse(KioskSettings.isValidPin("12a4"))
    }

    @Test
    fun defaultPin_matchesCurrentPolicy() {
        assertEquals("1234", KioskSettings.DEFAULT_PIN)
        assertTrue(KioskSettings.isValidPin(KioskSettings.DEFAULT_PIN))
    }

    @Test
    fun hashPin_isStableForSamePinAndSalt() {
        val salt = "0011223344556677"
        assertEquals(
            KioskSettings.hashPin("2468", salt),
            KioskSettings.hashPin("2468", salt)
        )
    }
}
