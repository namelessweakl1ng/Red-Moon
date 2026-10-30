package com.redmoon.vault.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BiometricEnvelopeTest {
    @Test fun envelopeRoundTripsWithoutPlaintextKey() {
        val value = BiometricEnvelope("redmoon.biometric.test", ByteArray(12) { 7 }, ByteArray(48) { 8 })
        val decoded = BiometricEnvelope.decode(value.encode())
        assertEquals(value.alias, decoded.alias)
        assertArrayEquals(value.iv, decoded.iv)
        assertArrayEquals(value.ciphertext, decoded.ciphertext)
    }

    @Test fun rejectsUnknownVersionAndMalformedLengths() {
        val valid = BiometricEnvelope("redmoon.biometric.test", ByteArray(12), ByteArray(48)).encode()
        val json = org.json.JSONObject(String(valid, Charsets.UTF_8))
        json.put("version", 2)
        assertThrows(IllegalArgumentException::class.java) { BiometricEnvelope.decode(json.toString().toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { BiometricEnvelope("redmoon.biometric.test", ByteArray(0), ByteArray(48)).encode() }
        assertThrows(IllegalArgumentException::class.java) { BiometricEnvelope.decode(ByteArray(20_000)) }
    }
}
