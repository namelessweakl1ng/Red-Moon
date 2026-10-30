package com.redmoon.vault.backup

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.*

/** SAF transfers ciphertext only. Provider read-back is distinct from remote synchronization. */
class BackupCoordinator(context: Context, private var changed: ((String) -> Unit)?) {
    private val app = context.applicationContext
    private val preferences =
        app.getSharedPreferences("backup-operation-metadata", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Synchronized
    private fun evidence(): BackupEvidence =
        BackupEvidence(
            preferences.getString(
                "operation",
                preferences.getString("status", "No backup saved yet."),
            )!!,
            preferences.getString("lastSaved", "None")!!,
            preferences.getString("lastVerified", "None")!!,
        )

    // Keep completion evidence durable before reporting success; this runs on the IO worker for
    // writes.
    @SuppressLint("ApplySharedPref", "UseKtx")
    @Synchronized
    private fun persist(value: BackupEvidence) {
        preferences
            .edit()
            .putString("operation", value.operation)
            .putString("lastSaved", value.lastSaved)
            .putString("lastVerified", value.lastVerified)
            .commit()
    }

    fun status(): String = evidence().summary()

    fun detach() {
        changed = null
    }

    private suspend fun update(message: String) {
        synchronized(this) { persist(evidence().attempt(message)) }
        withContext(Dispatchers.Main) { changed?.invoke(status()) }
    }

    fun pending() {
        synchronized(this) {
            persist(
                evidence()
                    .attempt(
                        "Backup awaiting destination or completion. If interrupted, create another backup; earlier files are unchanged."
                    )
            )
        }
        changed?.invoke(status())
    }

    fun cancelled() {
        scope.launch { update("Backup cancelled. Earlier backup files are unchanged.") }
    }

    fun export(uri: Uri, encrypted: ByteArray) {
        scope.launch {
            try {
                val result =
                    BackupTransfer.write(
                        encrypted,
                        {
                            app.contentResolver.openOutputStream(uri, "wt")
                                ?: throw IOException("Cannot open destination")
                        },
                        { app.contentResolver.openInputStream(uri) },
                    )
                val name = BackupTransfer.displayName {
                    app.contentResolver
                        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getString(0) else null }
                }
                val destination = "${uri.authority ?: "document provider"} / $name"
                val time = DateFormat.getDateTimeInstance().format(Date())
                synchronized(this@BackupCoordinator) {
                    persist(evidence().saved(destination, time, result.verified))
                }
                withContext(Dispatchers.Main) { changed?.invoke(status()) }
            } catch (_: Exception) {
                update(
                    "Backup failed or was interrupted. No cloud success was recorded. Earlier backup files are unchanged."
                )
            }
        }
    }

    fun read(uri: Uri, ready: (ByteArray) -> Unit, failed: (String) -> Unit) {
        scope.launch {
            try {
                val bytes =
                    app.contentResolver.openInputStream(uri)?.use { BackupTransfer.read(it) }
                        ?: throw IOException("Cannot read backup")
                withContext(Dispatchers.Main) { ready(bytes) }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    failed(
                        "Cannot read the encrypted backup. Check provider access, connectivity and file size. The active vault was not replaced."
                    )
                }
            }
        }
    }
}
