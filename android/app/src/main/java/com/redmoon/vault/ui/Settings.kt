package com.redmoon.vault.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redmoon.vault.VaultModel
import org.json.JSONObject

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun SettingsScreen(
    v: VaultModel,
    backup: () -> Unit,
    restore: () -> Unit,
    enrollBiometrics: (String) -> Unit,
    disableBiometrics: () -> Unit,
    biometricEnabled: Boolean,
    backupStatus: String,
) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var biometricPassword by remember { mutableStateOf("") }
    var deletePassword by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    var changed by remember { mutableStateOf(false) }
    val settings = v.snapshot.optJSONObject("settings") ?: JSONObject()
    fun setting(key: String, value: Any) {
        v.run(
            request("settings").put("settings", JSONObject(settings.toString()).put(key, value))
        ) {
            v.load()
        }
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item { Heading("Make it yours.", "Your vault. Your preferences.") }
        item {
            Section("Appearance")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system", "light", "dark").forEach { theme ->
                    FilterChip(
                        settings.optString("theme", "system") == theme,
                        { setting("theme", theme) },
                        { Text(theme.replaceFirstChar { it.uppercase() }) },
                        enabled = !v.busy,
                    )
                }
            }
        }
        item {
            Section("Auto-lock")
            Text(
                "Locks when the app leaves the foreground, and after inactivity.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60, 300, 600).forEach { seconds ->
                    FilterChip(
                        settings.optInt("auto_lock_seconds", 300) == seconds,
                        { setting("auto_lock_seconds", seconds) },
                        { Text("${seconds / 60} min") },
                        enabled = !v.busy,
                    )
                }
            }
        }
        item {
            Section("Biometric unlock")
            Text(
                "Use your device biometrics after setting up with your master password.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (biometricEnabled)
                OutlinedButton(onClick = disableBiometrics, enabled = !v.busy) {
                    Text("Disable biometric unlock")
                }
            else {
                Field("Master password for biometric setup", biometricPassword, true) {
                    biometricPassword = it
                }
                TextButton(
                    onClick = {
                        val password = biometricPassword
                        biometricPassword = ""
                        enrollBiometrics(password)
                    },
                    enabled = !v.busy && biometricPassword.isNotEmpty(),
                ) {
                    Text("Set up biometric unlock")
                }
            }
        }
        item {
            Section("Encrypted backups")
            Text(
                "Keep a recoverable encrypted copy separate from your phone.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = { v.screen = "BackupRecovery" },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Backup & Recovery")
            }
        }
        item {
            Section("Change master password")
            Field("Current master password", current, true) { current = it }
            Spacer(Modifier.height(8.dp))
            Field("New master password", new, true) { new = it }
            Spacer(Modifier.height(8.dp))
            Field("Confirm new password", confirm, true) { confirm = it }
            Text(
                "Use at least 14 characters. Existing backups retain their original password.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Primary(
                "Change master password",
                !v.busy &&
                    current.isNotEmpty() &&
                    new == confirm &&
                    new.codePointCount(0, new.length) >= 14,
            ) {
                v.run(request("change_password").put("current", current).put("new", new)) {
                    current = ""
                    new = ""
                    confirm = ""
                    changed = true
                }
            }
            if (changed) Text("Master password changed.", color = MaterialTheme.colorScheme.primary)
        }
        item {
            Section("About your privacy")
            Text(
                "Red Moon 2.0.0 · Argon2id + XChaCha20-Poly1305",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                "Your vault is encrypted on this device. Screenshots and Android app backups are disabled. Malware or a compromised device can still expose secrets. Red Moon has not been independently audited.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { deleting = true }, enabled = !v.busy) {
                Text("Delete vault", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (deleting)
        AlertDialog(
            onDismissRequest = {
                deleting = false
                deletePassword = ""
            },
            title = { Text("Permanently delete vault?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Deletes the active vault only. Encrypted backups and recovery safety copies are retained and must be removed separately. Export a backup first if you want to keep these records."
                    )
                    Field("Master password", deletePassword, true) { deletePassword = it }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        v.run(request("delete_vault").put("password", deletePassword)) {
                            deletePassword = ""
                            deleting = false
                            v.lock()
                        }
                    },
                    enabled = !v.busy && deletePassword.isNotEmpty(),
                ) {
                    Text("Delete vault", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deleting = false
                        deletePassword = ""
                    }
                ) {
                    Text("Cancel")
                }
            },
        )
}
