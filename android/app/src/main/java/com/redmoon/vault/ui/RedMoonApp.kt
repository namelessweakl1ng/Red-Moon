package com.redmoon.vault.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.redmoon.vault.R
import com.redmoon.vault.VaultModel
import org.json.JSONArray
import org.json.JSONObject

internal val kinds =
    linkedMapOf(
        "login" to "Logins",
        "secure_note" to "Notes",
        "identity" to "Identities",
        "indian_document" to "Documents",
        "financial" to "Financial",
        "custom" to "Custom",
    )

internal fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

internal fun request(action: String) = JSONObject().put("action", action)

internal fun JSONObject.payload(): JSONObject = getJSONObject("ok")

internal fun JSONObject.record(): JSONObject = optJSONObject("record") ?: this

internal fun VaultModel.home() {
    selected = null
    screen = "Vault"
    load()
}

@Composable
fun RedMoonApp(
    v: VaultModel,
    backup: () -> Unit,
    restore: () -> Unit,
    biometricUnlock: () -> Unit,
    enrollBiometrics: (String) -> Unit,
    disableBiometrics: () -> Unit,
    biometricEnabled: Boolean,
    backupStatus: String,
) {
    val theme = v.snapshot.optJSONObject("settings")?.optString("theme", "dark") ?: "dark"
    val dark = theme in listOf("dark", "contrast") || (theme == "system" && isSystemInDarkTheme())
    val colors =
        if (dark)
            darkColorScheme(
                primary = Color(0xFFFF6B7B),
                onPrimary = Color(0xFF3E0011),
                background = Color(0xFF101116),
                surface = Color(0xFF191B23),
                surfaceVariant = Color(0xFF252731),
                secondaryContainer = Color(0xFF3B2530),
            )
        else
            lightColorScheme(
                primary = Color(0xFFB62346),
                onPrimary = Color.White,
                background = Color(0xFFF9F7F8),
                surface = Color.White,
                surfaceVariant = Color(0xFFF0ECEF),
                secondaryContainer = Color(0xFFF9DFE6),
            )
    MaterialTheme(
        colorScheme = colors,
        shapes =
            Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(24.dp),
            ),
    ) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            key(v.uiEpoch, v.unlocked) {
                BackHandler(enabled = v.unlocked || v.screen == "Restore") {
                    if (v.screen == "Restore") {
                        v.pendingRestore = null
                        v.screen = "Vault"
                    } else if (v.screen == "BackupRecovery") {
                        v.screen = "Settings"
                    } else if (v.screen !in listOf("Vault", "Passwords")) {
                        v.selected = null
                        v.screen = "Vault"
                    } else v.lock()
                }
                if (!v.ready)
                    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp)) {
                        Column {
                            Brand()
                            Spacer(Modifier.height(32.dp))
                            Text("Preparing your private space…")
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (v.error.isNotBlank()) Text(v.error)
                        }
                    }
                else if (v.screen == "Restore") RestoreScreen(v)
                else if (!v.unlocked) AuthScreen(v, restore, biometricUnlock, biometricEnabled)
                else {
                    LaunchedEffect(v.unlocked) { v.load() }
                    val destination =
                        when (v.screen) {
                            "Passwords",
                            "Favorites" -> "Vault"
                            else -> if (v.screen.startsWith("Category:")) "Vault" else v.screen
                        }
                    Scaffold(
                        containerColor = colors.background,
                        bottomBar = {
                            if (destination in listOf("Vault", "Notes", "Categories", "Settings"))
                                NavigationBar(containerColor = colors.surface) {
                                    listOf(
                                            Triple("Vault", R.drawable.nav_vault, "Vault"),
                                            Triple("Notes", R.drawable.nav_notes, "Notes"),
                                            Triple(
                                                "Categories",
                                                R.drawable.nav_categories,
                                                "Categories",
                                            ),
                                            Triple("Settings", R.drawable.nav_settings, "Settings"),
                                        )
                                        .forEach { (page, glyph, label) ->
                                            NavigationBarItem(
                                                selected = destination == page,
                                                onClick = {
                                                    v.selected = null
                                                    v.screen = page
                                                },
                                                icon = {
                                                    Icon(
                                                        painterResource(glyph),
                                                        contentDescription = label,
                                                    )
                                                },
                                                label = { Text(label) },
                                            )
                                        }
                                }
                        },
                        floatingActionButton = {
                            if (destination == "Vault" || destination == "Notes")
                                ExtendedFloatingActionButton(
                                    onClick = {
                                        v.selected = null
                                        v.screen = if (destination == "Notes") "NewNote" else "New"
                                    },
                                    icon = {
                                        Text("+", style = MaterialTheme.typography.headlineSmall)
                                    },
                                    text = {
                                        Text(if (destination == "Notes") "New note" else "Add item")
                                    },
                                )
                        },
                    ) { padding ->
                        Column(
                            Modifier.padding(padding)
                                .safeDrawingPadding()
                                .imePadding()
                                .padding(horizontal = 20.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Brand()
                                TextButton(onClick = { v.lock() }) { Text("Lock") }
                            }
                            if (v.error.isNotBlank())
                                Text(
                                    v.error,
                                    color = colors.error,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            if (v.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                            when (destination) {
                                "Vault",
                                "Notes" ->
                                    VaultScreen(
                                        v,
                                        destination == "Notes",
                                        if (v.screen.startsWith("Category:"))
                                            v.screen.removePrefix("Category:")
                                        else "",
                                    )
                                "Categories" -> CategoriesScreen(v)
                                "Settings" ->
                                    SettingsScreen(
                                        v,
                                        backup,
                                        restore,
                                        enrollBiometrics,
                                        disableBiometrics,
                                        biometricEnabled,
                                        backupStatus,
                                    )
                                "BackupRecovery" ->
                                    BackupRecoveryScreen(backup, restore, !v.busy, backupStatus)
                                "Detail" -> RecordDetail(v)
                                "Edit",
                                "New",
                                "NewNote" -> RecordEditor(v, destination == "NewNote")
                                else -> VaultScreen(v, false)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Brand() {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Image(
            painterResource(R.drawable.red_moon_mark),
            contentDescription = null,
            modifier = Modifier.size(38.dp),
        )
        Column {
            Text(
                "RED MOON",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Your private space",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun Heading(title: String, subtitle: String = "") {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
    if (subtitle.isNotBlank())
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )
}

@Composable
internal fun Section(title: String) {
    Text(
        title,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
    )
}

internal fun JSONObject.matchesCategory(value: String): Boolean =
    optString("id").equals(value, true) || optString("name").equals(value, true)
