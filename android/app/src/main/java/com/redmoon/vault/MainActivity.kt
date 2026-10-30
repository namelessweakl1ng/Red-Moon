package com.redmoon.vault

import android.os.Bundle
import android.util.Base64
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val vault: VaultModel by viewModels()
    private val export =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            val bytes = vault.pendingBackup
            vault.pendingBackup = null
            if (uri != null && bytes != null)
                lifecycleIo {
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        ?: error("Cannot open backup destination")
                }
        }
    private val importPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                lifecycleIo {
                    val bytes =
                        contentResolver.openInputStream(uri)?.use { stream ->
                            val out = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = stream.read(buffer)
                                if (count < 0) break
                                require(out.size() + count <= 16 * 1024 * 1024) {
                                    "Backup too large"
                                }
                                out.write(buffer, 0, count)
                            }
                            out.toByteArray()
                        } ?: error("Cannot read backup")
                    require(bytes.size <= 16 * 1024 * 1024) { "Backup exceeds vault size limit" }
                    runOnUiThread {
                        vault.pendingRestore = bytes
                        vault.screen = "Restore"
                    }
                }
        }

    private fun lifecycleIo(block: () -> Unit) {
        Thread {
                try {
                    block()
                } catch (_: Exception) {
                    runOnUiThread {
                        android.widget.Toast.makeText(
                                this,
                                "Backup operation failed. Existing vault was not replaced.",
                                android.widget.Toast.LENGTH_LONG,
                            )
                            .show()
                    }
                }
            }
            .start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.importantForAutofill =
            android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        setContent {
            RedMoon(
                vault,
                backup = {
                    vault.run(JSONObject().put("action", "backup")) {
                        vault.pendingBackup =
                            Base64.decode(it.getJSONObject("ok").getString("data"), Base64.NO_WRAP)
                        export.launch("red-moon-backup.rm")
                    }
                },
                restore = { importPicker.launch(arrayOf("application/octet-stream", "*/*")) },
            )
        }
    }

    override fun onPause() {
        super.onPause()
        vault.lock()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) vault.activity()
        return super.dispatchTouchEvent(event)
    }
}

private val Red = Color(0xFFE11D48)

@Composable
private fun Input(label: String, value: String, secret: Boolean = false, change: (String) -> Unit) {
    OutlinedTextField(
        value,
        onValueChange = change,
        label = { Text(label) },
        visualTransformation =
            if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.ui.graphics.RectangleShape,
        keyboardOptions =
            KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType =
                    if (secret) androidx.compose.ui.text.input.KeyboardType.Password
                    else androidx.compose.ui.text.input.KeyboardType.Text,
            ),
    )
}

@Composable
private fun Action(label: String, enabled: Boolean = true, click: () -> Unit) {
    Button(
        onClick = click,
        enabled = enabled,
        shape = androidx.compose.ui.graphics.RectangleShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
    }
}

@Composable
fun RedMoon(v: VaultModel, backup: () -> Unit, restore: () -> Unit) {
    MaterialTheme(
        shapes =
            Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                small = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            ),
        colorScheme =
            darkColorScheme(
                primary = Red,
                background = Color(0xFF0B0B10),
                surface = Color(0xFF17171F),
                onPrimary = Color.White,
            ),
    ) {
        BackHandler(enabled = v.unlocked || v.screen == "Restore") {
            if (v.screen == "Restore") {
                v.pendingRestore = null
                v.screen = "Passwords"
            } else if (v.selected != null || v.screen != "Passwords") {
                v.selected = null
                v.password = ""
                v.notes = ""
                v.screen = "Passwords"
            } else v.lock()
        }
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.safeDrawingPadding().imePadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("RED MOON", style = MaterialTheme.typography.headlineSmall, color = Red)
                if (v.error.isNotEmpty()) Text(v.error, color = MaterialTheme.colorScheme.error)
                if (!v.ready) {
                    Text("Preparing encrypted storage…")
                    return@Column
                }
                key(v.unlocked, v.screen, v.uiEpoch) {
                    if (v.screen == "Restore") Restore(v)
                    else if (!v.unlocked) Auth(v, restore)
                    else {
                        LaunchedEffect(v.unlocked) { v.load() }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(v.screen)
                            TextButton(onClick = { v.lock() }) { Text("Lock") }
                        }
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { menu = true }) { Text("Navigate ▾") }
                            DropdownMenu(menu, { menu = false }) {
                                listOf("Passwords", "Favorites", "Generator", "Backups", "Settings")
                                    .forEach { page ->
                                        DropdownMenuItem(
                                            text = { Text(page) },
                                            onClick = {
                                                v.selected = null
                                                v.password = ""
                                                v.notes = ""
                                                v.screen = page
                                                menu = false
                                            },
                                        )
                                    }
                            }
                        }
                        when (v.screen) {
                            "Passwords",
                            "Favorites" -> if (v.selected != null) Detail(v) else Entries(v)
                            "Edit" -> Editor(v)
                            "Generator" -> Generator(v)
                            "Settings" -> Settings(v)
                            "Backups" -> {
                                Text(
                                    "Encrypted backups retain the master password used when they were created."
                                )
                                Action("Save encrypted backup", !v.busy, backup)
                                Action("Choose encrypted backup", !v.busy, restore)
                            }
                        }
                    }
                }
                if (v.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Auth(v: VaultModel, restore: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }
    Text("Your secrets. Your control.", style = MaterialTheme.typography.headlineMedium)
    Input("Master password", password, true) { password = it }
    if (!v.exists) {
        Text(assessment(password))
        Input("Confirm master password", confirmation, true) { confirmation = it }
    }
    if (mismatch) Text("Use at least 14 characters and matching passwords.")
    Action(if (v.exists) "Unlock vault" else "Create vault", !v.busy) {
        if (
            !v.exists &&
                (password != confirmation || password.codePointCount(0, password.length) < 14)
        )
            mismatch = true
        else
            v.run(
                JSONObject()
                    .put("action", if (v.exists) "unlock" else "create")
                    .put("password", password)
            ) {
                password = ""
                confirmation = ""
            }
    }
    Text(
        "If you forget your master password, recovery may be permanently impossible. No accounts. No cloud."
    )
    Action("Restore encrypted backup", !v.busy, restore)
}

@Composable
private fun Entries(v: VaultModel) {
    var query by remember { mutableStateOf("") }
    Input("Search names, categories and tags", query) {
        query = it
        v.activity()
    }
    Action("Add password", !v.busy) {
        v.selected = null
        v.password = ""
        v.notes = ""
        v.screen = "Edit"
    }
    val entries = v.snapshot.optJSONArray("entries") ?: JSONArray()
    val rows =
        (0 until entries.length())
            .map { entries.getJSONObject(it) }
            .filter {
                (v.screen != "Favorites" || it.optBoolean("favorite")) &&
                    listOf("title", "username", "category", "tags", "url").any { key ->
                        it.optString(key).contains(query, true)
                    }
            }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (rows.isEmpty()) item { Text("No matching passwords. Add your first account.") }
        items(rows, key = { it.getString("id") }) { e ->
            OutlinedButton(
                onClick = { v.select(e) },
                shape = androidx.compose.ui.graphics.RectangleShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(e.getString("title"))
                    Text(e.getString("username"))
                    if (e.optBoolean("duplicate")) Text("Possible duplicate", color = Red)
                }
            }
        }
    }
}

@Composable
private fun Detail(v: VaultModel) {
    val e = v.selected ?: return
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(e.getString("title"), style = MaterialTheme.typography.headlineMedium)
            Text(e.optString("username"))
            Action("Copy username", !v.busy) { v.copy(e, false) }
        }
        item {
            Text(v.password.ifEmpty { "••••••••••••" })
            Action(if (v.password.isEmpty()) "Reveal password" else "Hide password", !v.busy) {
                if (v.password.isNotEmpty()) v.password = ""
                else
                    v.run(JSONObject().put("action", "reveal").put("id", e.getString("id"))) {
                        v.password = it.getJSONObject("ok").getString("password")
                    }
            }
            Action("Copy password (20 seconds)", !v.busy) { v.copy(e, true) }
        }
        item {
            Text(e.optString("url"))
            Text("Category: ${e.optString("category")}")
            Text("Tags: ${e.optJSONArray("tags")}")
            Text(v.notes.ifEmpty { "No secure notes" })
            Text("Created: ${java.util.Date(e.optLong("created_at") * 1000)}")
            Text("Modified: ${java.util.Date(e.optLong("modified_at") * 1000)}")
        }
        item {
            Action("Edit", !v.busy) {
                v.run(JSONObject().put("action", "reveal").put("id", e.getString("id"))) {
                    v.password = it.getJSONObject("ok").getString("password")
                    v.screen = "Edit"
                }
            }
            Action(if (e.optBoolean("favorite")) "Remove favorite" else "Favorite", !v.busy) {
                v.run(JSONObject().put("action", "favorite").put("id", e.getString("id"))) {
                    v.selected = null
                    v.load()
                }
            }
        }
        item {
            var confirm by remember { mutableStateOf(false) }
            Action("Delete entry", !v.busy) { confirm = true }
            if (confirm) {
                Text("Delete permanently from this vault?")
                Action("Confirm delete", !v.busy) {
                    v.run(JSONObject().put("action", "delete").put("id", e.getString("id"))) {
                        v.selected = null
                        v.password = ""
                        v.notes = ""
                        v.load()
                    }
                }
            }
            Action("Back to list") {
                v.selected = null
                v.password = ""
                v.notes = ""
            }
        }
    }
}

@Composable
private fun Editor(v: VaultModel) {
    val original = v.selected
    var title by remember { mutableStateOf(original?.optString("title") ?: "") }
    var username by remember { mutableStateOf(original?.optString("username") ?: "") }
    var password by remember { mutableStateOf(v.password) }
    var url by remember { mutableStateOf(original?.optString("url") ?: "") }
    var category by remember { mutableStateOf(original?.optString("category") ?: "") }
    var tags by remember {
        mutableStateOf(
            original?.optJSONArray("tags")?.let { a ->
                (0 until a.length()).joinToString(", ") { a.getString(it) }
            } ?: ""
        )
    }
    var notes by remember { mutableStateOf(v.notes) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Input("Name", title) {
                title = it
                v.activity()
            }
            Input("Username", username) {
                username = it
                v.activity()
            }
            Input("Password", password, true) {
                password = it
                v.activity()
            }
            Text(assessment(password))
            Action("Generate 24-character password", !v.busy) {
                v.run(generateRequest(24, true, true, true, true)) {
                    password = it.getJSONObject("ok").getString("password")
                }
            }
        }
        item {
            Input("URL", url) {
                url = it
                v.activity()
            }
            Input("Category", category) {
                category = it
                v.activity()
            }
            Input("Tags, comma separated", tags) {
                tags = it
                v.activity()
            }
            Input("Secure notes", notes) {
                notes = it
                v.activity()
            }
        }
        item {
            Action("Save encrypted entry", !v.busy && title.isNotBlank()) {
                val e =
                    JSONObject()
                        .put("id", original?.optString("id") ?: "")
                        .put("title", title)
                        .put("username", username)
                        .put("password", password)
                        .put("url", url)
                        .put("category", category)
                        .put(
                            "tags",
                            JSONArray(tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }),
                        )
                        .put("notes", notes)
                        .put("favorite", original?.optBoolean("favorite") ?: false)
                        .put("created_at", 0)
                        .put("modified_at", 0)
                v.run(JSONObject().put("action", "save").put("entry", e)) {
                    password = ""
                    notes = ""
                    v.password = ""
                    v.notes = ""
                    v.selected = null
                    v.screen = "Passwords"
                    v.load()
                }
            }
            Action("Cancel") {
                v.password = ""
                v.notes = ""
                v.selected = null
                v.screen = "Passwords"
            }
        }
    }
}

private fun generateRequest(
    length: Int,
    lower: Boolean,
    upper: Boolean,
    digits: Boolean,
    symbols: Boolean,
) =
    JSONObject()
        .put("action", "generate")
        .put(
            "options",
            JSONObject()
                .put("length", length)
                .put("lowercase", lower)
                .put("uppercase", upper)
                .put("digits", digits)
                .put("symbols", symbols),
        )

@Composable
private fun Generator(v: VaultModel) {
    var length by remember { mutableFloatStateOf(24f) }
    var lower by remember { mutableStateOf(true) }
    var upper by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var generated by remember { mutableStateOf("") }
    Text("Length: ${length.toInt()}")
    Slider(length, { length = it }, valueRange = 12f..128f, steps = 115)
    listOf(
            Triple("Lowercase", lower, { b: Boolean -> lower = b }),
            Triple("Uppercase", upper, { b: Boolean -> upper = b }),
            Triple("Digits", digits, { b: Boolean -> digits = b }),
            Triple("Symbols", symbols, { b: Boolean -> symbols = b }),
        )
        .forEach { (label, checked, change) ->
            Row {
                Checkbox(checked, change)
                Text(label)
            }
        }
    Text(generated.ifEmpty { "Generate a unique password for every account." })
    Text("Longer random passwords resist guessing better. No password is sent to a service.")
    Action("Generate", !v.busy && (lower || upper || digits || symbols)) {
        v.run(generateRequest(length.toInt(), lower, upper, digits, symbols)) {
            generated = it.getJSONObject("ok").getString("password")
        }
    }
    Action("Copy generated password", generated.isNotEmpty()) { v.copyText(generated, true) }
}

@Composable
private fun Settings(v: VaultModel) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Auto-lock")
            listOf(60L, 300L, 600L).forEach { seconds ->
                Action("${seconds / 60} minutes", !v.busy) {
                    v.run(
                        JSONObject()
                            .put("action", "settings")
                            .put(
                                "settings",
                                JSONObject().put("auto_lock_seconds", seconds).put("theme", "dark"),
                            )
                    ) {
                        v.load()
                    }
                }
            }
            Text(
                "The vault locks whenever the app leaves the foreground. Screenshots and OS backups are disabled."
            )
        }
        item {
            Text("Change master password")
            Input("Current password", current, true) { current = it }
            Input("New master password", new, true) { new = it }
            Input("Confirm new password", confirm, true) { confirm = it }
            Action("Change password", !v.busy && new == confirm && new.length >= 14) {
                v.run(
                    JSONObject()
                        .put("action", "change_password")
                        .put("current", current)
                        .put("new", new)
                ) {
                    current = ""
                    new = ""
                    confirm = ""
                }
            }
        }
        item {
            Text(
                "Locked vaults protect against file theft. Malware, keyloggers and a compromised OS can defeat these protections. This application has not been independently audited. Old backups keep their old master password."
            )
        }
    }
}

@Composable
private fun Restore(v: VaultModel) {
    var current by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }
    Text("Restore encrypted backup")
    if (v.exists) Input("Current master password", current, true) { current = it }
    Input("Backup master password", password, true) { password = it }
    Row {
        Checkbox(confirmed, { confirmed = it })
        Text("Replace my current vault after validation. An encrypted safety copy is preserved.")
    }
    Action("Validate and restore", !v.busy && confirmed && v.pendingRestore != null) {
        val data = v.pendingRestore ?: return@Action
        v.run(
            JSONObject()
                .put("action", "restore")
                .put("data", Base64.encodeToString(data, Base64.NO_WRAP))
                .put("password", password)
                .put("current", if (v.exists) current else JSONObject.NULL)
        ) {
            current = ""
            password = ""
            v.pendingRestore = null
            v.screen = "Passwords"
        }
    }
    Action("Cancel") {
        v.pendingRestore = null
        v.screen = "Passwords"
    }
}

private fun assessment(password: String): String =
    when {
        password.isEmpty() -> "Use a long unique passphrase or a generated password."
        password.length < 14 -> "Short: use at least 14 characters for a master passphrase."
        Regex("password|123456|qwerty|letmein", RegexOption.IGNORE_CASE)
            .containsMatchIn(password) ->
            "Predictable sequence detected. Choose a less guessable password."
        password.length < 20 -> "Moderate length. A longer random password is recommended."
        else -> "Long: keep it unpredictable and unique. This is only an estimate."
    }
