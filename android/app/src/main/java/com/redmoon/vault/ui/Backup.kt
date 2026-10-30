package com.redmoon.vault.ui

import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redmoon.vault.VaultModel
import org.json.JSONObject

@Composable
internal fun BackupControls(
    backup: () -> Unit,
    restore: () -> Unit,
    enabled: Boolean,
    status: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Backups are encrypted files. Choose Google Drive in the system file picker, or save a local copy. The selected provider controls where the file is stored; this is not automatic sync.",
            style = MaterialTheme.typography.bodySmall,
        )
        Primary("Back up to Google Drive", enabled, backup)
        OutlinedButton(onClick = backup, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("Save local encrypted backup")
        }
        OutlinedButton(onClick = restore, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("Restore encrypted backup")
        }
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
        Text(
            "Automatic cloud backup is not implemented in this version.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun RestoreScreen(v: VaultModel) {
    var current by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }
    val report = v.restoreReport
    Column(
        Modifier.safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Brand()
        Spacer(Modifier.height(12.dp))
        Heading(
            "Restore your vault.",
            "Validate an encrypted backup before replacing your current vault.",
        )
        if (v.error.isNotBlank()) Text(v.error, color = MaterialTheme.colorScheme.error)
        if (report.isNotBlank()) {
            Text(report)
            Primary("Continue") {
                v.screen = "Vault"
                if (v.unlocked) v.load()
            }
            return@Column
        }
        if (v.exists) Field("Current master password", current, true) { current = it }
        Field("Backup master password", password, true) { password = it }
        Row {
            Checkbox(confirmed, { confirmed = it })
            Text(
                "Replace the current vault after the backup is validated. An encrypted safety copy is preserved.",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Primary(
            "Validate and restore",
            !v.busy && confirmed && password.isNotEmpty() && v.pendingRestore != null,
        ) {
            val bytes = v.pendingRestore ?: return@Primary
            v.run(
                request("restore")
                    .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                    .put("password", password)
                    .put("current", if (v.exists) current else JSONObject.NULL)
            ) { response ->
                current = ""
                password = ""
                v.pendingRestore = null
                val ok = response.payload()
                val count =
                    when {
                        ok.has("record_count") -> ok.optInt("record_count")
                        ok.has("records") && ok.opt("records") is Number -> ok.optInt("records")
                        ok.has("entries") && ok.opt("entries") is Number -> ok.optInt("entries")
                        else -> -1
                    }
                v.restoreReport =
                    if (count >= 0) "Restore complete. $count items restored."
                    else "Restore complete. Your encrypted backup was validated and restored."
            }
        }
        TextButton(
            onClick = {
                v.pendingRestore = null
                current = ""
                password = ""
                v.screen = "Vault"
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cancel")
        }
        if (v.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
internal fun BackupRecoveryScreen(
    backup: () -> Unit,
    restore: () -> Unit,
    enabled: Boolean,
    status: String,
) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Heading(
            "Backup & Recovery",
            "Your phone can be replaced. Your secrets should be recoverable.",
        )
        BackupControls(backup, restore, enabled, status)
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Recover on a new phone", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Install Red Moon, choose Restore existing vault, select your encrypted .rm backup and enter that backup's master password. Your old phone and biometric keys are not required."
                )
                Text(
                    "Old backups keep their old password after a password change. A forgotten master password cannot be reset. Keep a separately verified copy before replacing or uninstalling the app."
                )
                Text(
                    "Google Drive saves require its document provider and connectivity. Local providers work offline. A provider write or read-back is not proof of cloud synchronization.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
