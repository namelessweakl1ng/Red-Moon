package com.redmoon.vault.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class BackupTransferTest {
    @Test
    fun unavailableOptionalMetadataKeepsSuccessfulWriteUsable() {
        assertEquals(
            "Encrypted .rm file",
            BackupTransfer.displayName {
                throw IOException("Provider does not expose display names")
            },
        )
    }

    private val encrypted = ByteArray(128) { (it * 7).toByte() }

    @Test
    fun successfulProviderWriteIsVerifiedByReadBack() {
        val destination = ByteArrayOutputStream()
        val result =
            BackupTransfer.write(
                encrypted,
                { destination },
                { ByteArrayInputStream(destination.toByteArray()) },
            )
        assertArrayEquals(encrypted, destination.toByteArray())
        assertTrue(result.verified)
    }

    @Test
    fun cloudReadUnavailableDoesNotClaimSynchronizationOrVerification() {
        val result =
            BackupTransfer.write(
                encrypted,
                { ByteArrayOutputStream() },
                { throw IOException("provider offline") },
            )
        assertFalse(result.verified)
        assertEquals(128, result.bytes)
    }

    @Test
    fun mismatchedReadBackIsReportedAsUnverified() {
        val result =
            BackupTransfer.write(
                encrypted,
                { ByteArrayOutputStream() },
                { ByteArrayInputStream(byteArrayOf(1)) },
            )
        assertFalse(result.verified)
    }

    @Test
    fun failedWriteDoesNotMarkProviderSuccess() {
        try {
            BackupTransfer.write(encrypted, { throw IOException("full storage") }, { null })
            fail("Expected failure")
        } catch (_: IOException) {}
    }

    @Test
    fun oversizedRestoreIsBoundedBeforeAllocationGrowth() {
        val endless =
            object : java.io.InputStream() {
                override fun read() = 0

                override fun read(buffer: ByteArray, off: Int, len: Int): Int {
                    buffer.fill(0, off, off + len)
                    return len
                }
            }
        try {
            BackupTransfer.read(endless, 128)
            fail("Expected size rejection")
        } catch (_: IllegalArgumentException) {}
    }
}
