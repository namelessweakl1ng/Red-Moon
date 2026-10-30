package com.redmoon.vault

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.os.PersistableBundle
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.*
import org.json.JSONObject

class VaultModel(application: Application) : AndroidViewModel(application) {
    private val worker = NativeVault.dispatcher
    private var navigationEpoch = 0L
    private var epoch = 0L
    var uiEpoch by mutableLongStateOf(0L)
        private set

    var ready by mutableStateOf(false)
        private set

    var unlocked by mutableStateOf(false)
        private set

    var exists by mutableStateOf(false)
        private set

    var snapshot by mutableStateOf(JSONObject())
        private set

    var error by mutableStateOf("")
        private set

    var busy by mutableStateOf(false)
        private set

    private var screenState by mutableStateOf("Passwords")
    var screen: String
        get() = screenState
        set(value) {
            navigationEpoch++
            screenState = value
        }

    private var selectedState by mutableStateOf<JSONObject?>(null)
    var selected: JSONObject?
        get() = selectedState
        set(value) {
            navigationEpoch++
            selectedState = value
        }

    var password by mutableStateOf("")
    var notes by mutableStateOf("")
    var pendingBackup: ByteArray? = null
    var pendingRestore: ByteArray? = null
    private val clipboard = application.getSystemService(ClipboardManager::class.java)
    private var clipboardToken = 0L
    private var copiedPassword: String? = null

    private fun native(r: JSONObject): JSONObject {
        val response =
            JSONObject(NativeVault.request(r.toString(), SystemClock.elapsedRealtime() / 1000))
        if (response.has("error")) throw IllegalStateException(response.getString("error"))
        return response
    }

    init {
        viewModelScope.launch {
            try {
                withContext(worker) {
                    val r =
                        JSONObject(
                            NativeVault.initialize(
                                File(application.filesDir, "vault.rm").absolutePath
                            )
                        )
                    check(!r.has("error")) { r.optString("error") }
                    native(JSONObject().put("action", "lock"))
                }
                refresh()
                ready = true
            } catch (_: LinkageError) {
                error =
                    "Native vault library unavailable. Build both Rust Android targets before installing."
            } catch (e: Exception) {
                error = e.message ?: "Vault unavailable"
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                if (ready && !busy)
                    try {
                        refresh()
                    } catch (_: Exception) {}
            }
        }
    }

    suspend fun refresh() {
        val generation = epoch
        val s =
            withContext(worker) { native(JSONObject().put("action", "status")).getJSONObject("ok") }
        if (generation != epoch) return
        exists = s.getBoolean("exists")
        if (!s.getBoolean("unlocked")) {
            if (unlocked) clearUi()
            unlocked = false
        } else unlocked = true
    }

    private fun clearUi() {
        epoch++
        uiEpoch++
        snapshot = JSONObject()
        selected = null
        password = ""
        notes = ""
        screen = "Passwords"
        try {
            clearOwnedClipboard()
        } catch (_: SecurityException) {
            copiedPassword = null
        }
    }

    fun lock() {
        NativeVault.enqueueLock()
        clearUi()
        unlocked = false
    }

    fun activity() {
        if (unlocked)
            viewModelScope.launch {
                try {
                    withContext(worker) { native(JSONObject().put("action", "activity")) }
                } catch (_: Exception) {
                    lock()
                }
            }
    }

    fun run(request: JSONObject, done: (JSONObject) -> Unit = {}) {
        if (busy) return
        val generation = epoch
        val navigation = navigationEpoch
        busy = true
        error = ""
        viewModelScope.launch {
            try {
                val result = withContext(worker) { native(request) }
                if (epoch == generation) {
                    if (navigation == navigationEpoch) done(result)
                    refresh()
                }
            } catch (e: Exception) {
                if (epoch == generation) error = e.message ?: "Operation failed"
            } finally {
                busy = false
            }
        }
    }

    fun load() {
        val generation = epoch
        viewModelScope.launch {
            try {
                val result = withContext(worker) { native(JSONObject().put("action", "list")) }
                if (epoch == generation) snapshot = result.getJSONObject("ok")
            } catch (e: Exception) {
                if (epoch == generation) error = e.message ?: "Cannot load vault"
            }
        }
    }

    fun select(entry: JSONObject) {
        if (busy) return
        selected = entry
        password = ""
        notes = ""
        run(JSONObject().put("action", "detail").put("id", entry.getString("id"))) {
            notes = it.getJSONObject("ok").getString("notes")
        }
    }

    fun copy(entry: JSONObject, secret: Boolean) =
        run(
            JSONObject()
                .put("action", if (secret) "copy_password" else "copy_username")
                .put("id", entry.getString("id"))
        ) {
            copyText(it.getJSONObject("ok").getString("text"), secret)
        }

    fun copyText(text: String, secret: Boolean) {
        clipboardToken++
        val token = clipboardToken
        val clip = ClipData.newPlainText("Red Moon", text)
        if (secret)
            clip.description.extras =
                PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        clipboard.setPrimaryClip(clip)
        copiedPassword = if (secret) text else null
        if (secret)
            viewModelScope.launch {
                delay(20_000)
                if (token == clipboardToken) clearOwnedClipboard()
            }
    }

    private fun clearOwnedClipboard() {
        val owned = copiedPassword ?: return
        if (clipboard.primaryClip?.getItemAt(0)?.text?.toString() == owned) {
            if (android.os.Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
            else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
        copiedPassword = null
    }
}
