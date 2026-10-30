package com.redmoon.vault.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                primary = Color(0xFFFB7185),
                onPrimary = Color(0xFF220711),
                background = Color(0xFF0B0B10),
                surface = Color(0xFF14141B),
                surfaceVariant = Color(0xFF202029),
                onSurfaceVariant = Color(0xFF9CA3AF),
                outline = Color(0xFF737382),
                outlineVariant = Color(0xFF292932),
                secondaryContainer = Color(0xFF22222C),
                onSecondaryContainer = Color(0xFFF8FAFC),
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
                extraSmall = RoundedCornerShape(0.dp),
                small = RoundedCornerShape(0.dp),
                medium = RoundedCornerShape(0.dp),
                large = RoundedCornerShape(0.dp),
                extraLarge = RoundedCornerShape(0.dp),
            ),
        typography =
            Typography(
                headlineMedium =
                    TextStyle(
                        fontSize = 28.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.8).sp,
                    ),
                titleLarge =
                    TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
                titleMedium =
                    TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
                bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
                bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
                bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
                labelLarge =
                    TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
                labelSmall =
                    TextStyle(fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp),
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
                    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                    Scaffold(
                        containerColor = colors.background,
                        bottomBar = {
                            if (
                                !keyboardOpen &&
                                    destination in
                                        listOf("Vault", "Notes", "Categories", "Settings")
                            )
                                FlatNavigation(destination) { page ->
                                    v.selected = null
                                    v.screen = page
                                }
                        },
                        floatingActionButton = {
                            if (!keyboardOpen && (destination == "Vault" || destination == "Notes"))
                                ExtendedFloatingActionButton(
                                    shape = RectangleShape,
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
            modifier = Modifier.size(30.dp),
        )
        Column {
            Text(
                "RED MOON",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "PRIVATE VAULT",
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

@Composable
private fun FlatNavigation(destination: String, navigate: (String) -> Unit) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth()) {
            listOf(
                    Triple("Vault", R.drawable.nav_vault, "Vault"),
                    Triple("Notes", R.drawable.nav_notes, "Notes"),
                    Triple("Categories", R.drawable.nav_categories, "Categories"),
                    Triple("Settings", R.drawable.nav_settings, "Settings"),
                )
                .forEach { (page, icon, label) ->
                    val selected = destination == page
                    val color =
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(
                        Modifier.weight(1f)
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = { navigate(page) },
                            )
                            .heightIn(min = 72.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.fillMaxWidth()
                                .height(2.dp)
                                .background(if (selected) color else Color.Transparent)
                        )
                        Icon(
                            painterResource(icon),
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.padding(top = 13.dp, bottom = 6.dp).size(20.dp),
                        )
                        Text(
                            label,
                            color = color,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }
        }
    }
}
