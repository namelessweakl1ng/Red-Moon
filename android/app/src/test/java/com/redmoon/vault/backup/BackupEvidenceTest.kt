package com.redmoon.vault.backup

import org.junit.Assert.*
import org.junit.Test

class BackupEvidenceTest {
    @Test
    fun failedOrCancelledAttemptRetainsKnownGoodBackupDestinations() {
        val verified = BackupEvidence().saved("Drive / first.rm", "time one", true)
        val cancelled = verified.attempt("Cancelled")
        val failed = cancelled.attempt("Storage full")
        assertTrue(failed.lastSaved.contains("first.rm"))
        assertTrue(failed.lastVerified.contains("first.rm"))
        assertEquals("Storage full", failed.operation)
    }

    @Test
    fun newerUnverifiedSaveDoesNotReplaceLastVerifiedReadback() {
        val evidence =
            BackupEvidence()
                .saved("Drive / first.rm", "time one", true)
                .saved("Local / newer.rm", "time two", false)
        assertTrue(evidence.lastSaved.contains("newer.rm"))
        assertTrue(evidence.lastVerified.contains("first.rm"))
    }
}
