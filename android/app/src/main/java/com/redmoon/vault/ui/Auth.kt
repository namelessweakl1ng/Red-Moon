package com.redmoon.vault.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.redmoon.vault.VaultModel

@Composable
internal fun Field(
    label: String,
    value: String,
    secret: Boolean = false,
    multiline: Boolean = false,
    type: String = "text",
    change: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = change,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        visualTransformation =
            if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions =
            KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType =
                    when {
                        secret || multiline -> KeyboardType.Password
                        type == "number" -> KeyboardType.Number
                        type == "url" -> KeyboardType.Uri
                        else -> KeyboardType.Text
                    },
            ),
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
internal fun Primary(label: String, enabled: Boolean = true, click: () -> Unit) {
    Button(
        onClick = click,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Text(label)
    }
}

@Composable
internal fun AuthScreen(
    v: VaultModel,
    restore: () -> Unit,
    biometricUnlock: () -> Unit,
    biometricEnabled: Boolean,
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Column(
        Modifier.safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Brand()
        Spacer(Modifier.height(24.dp))
        Heading(
            if (v.exists) "Unlock your vault" else "Create your vault",
            if (v.exists) "Enter your master password to continue."
            else "One master password. Your details, kept private.",
        )
        if (v.error.isNotBlank()) Text(v.error, color = MaterialTheme.colorScheme.error)
        Field("Master password", password, true) { password = it }
        if (!v.exists) {
            Field("Confirm master password", confirm, true) { confirm = it }
            Text(
                "Use at least 14 characters. Keep your master password somewhere safe: it cannot be recovered.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Primary(
            if (v.exists) "Unlock vault" else "Create vault",
            !v.busy &&
                password.isNotEmpty() &&
                (v.exists ||
                    (password == confirm && password.codePointCount(0, password.length) >= 14)),
        ) {
            v.run(request(if (v.exists) "unlock" else "create").put("password", password)) {
                password = ""
                confirm = ""
                v.screen = "Vault"
            }
        }
        if (v.exists && biometricEnabled)
            OutlinedButton(
                onClick = biometricUnlock,
                enabled = !v.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Unlock with biometrics")
            }
        TextButton(onClick = restore, enabled = !v.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Restore encrypted backup")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Encrypted on this device. No account required.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (v.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
