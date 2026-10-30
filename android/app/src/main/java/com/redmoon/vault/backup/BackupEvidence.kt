package com.redmoon.vault.backup

/** Non-secret recovery evidence must survive later cancelled, failed or unverified attempts. */
data class BackupEvidence(
    val operation: String = "No backup saved yet.",
    val lastSaved: String = "None",
    val lastVerified: String = "None",
) {
    fun attempt(message: String): BackupEvidence = copy(operation = message)

    fun saved(destination: String, time: String, verified: Boolean): BackupEvidence {
        val evidence = "$destination at $time"
        return copy(
            operation =
                if (verified)
                    "Provider read-back verified. Cloud synchronization is controlled by the provider, not confirmed by Red Moon."
                else
                    "Provider write completed; read-back was not verified. Keep earlier backups and test a restore. Cloud synchronization is not confirmed.",
            lastSaved = evidence,
            lastVerified = if (verified) evidence else lastVerified,
        )
    }

    fun summary(): String =
        "$operation\nLast successful provider write: $lastSaved\nLast verified provider read-back: $lastVerified"
}
