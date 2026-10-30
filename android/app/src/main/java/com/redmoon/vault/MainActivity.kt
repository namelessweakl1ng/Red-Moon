package com.redmoon.vault

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Base64
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.redmoon.vault.backup.BackupCoordinator
import com.redmoon.vault.security.BiometricVault
import com.redmoon.vault.ui.RedMoonApp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : FragmentActivity() {
    private val vault: VaultModel by viewModels()
    private lateinit var backupCoordinator: BackupCoordinator
    private lateinit var biometric: BiometricVault
    private var biometricEnabled by mutableStateOf(false)
    private var backupStatus by mutableStateOf("")
    private var biometricEpoch = -1L
    private val screenOff =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) secureBackground()
            }
        }
    private val export =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            val bytes = vault.pendingBackup
            vault.pendingBackup = null
            if (uri != null && bytes != null) backupCoordinator.export(uri, bytes)
            else if (uri == null) backupCoordinator.cancelled()
            else
                vault.reportError(
                    "The backup was interrupted before data could be written. Create another backup; earlier backups are unchanged."
                )
        }
    private val importPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                backupCoordinator.read(
                    uri,
                    ready = { bytes ->
                        vault.pendingRestore = bytes
                        vault.restoreReport = ""
                        vault.screen = "Restore"
                    },
                    failed = { vault.reportError(it) },
                )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.importantForAutofill =
            android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        backupCoordinator = BackupCoordinator(applicationContext) { backupStatus = it }
        backupStatus = backupCoordinator.status()
        biometric =
            BiometricVault(
                this,
                exportKey = { password -> vault.exportBiometricKey(password, biometricEpoch) },
                unlockKey = { key -> vault.unlockBiometricKey(key, biometricEpoch) },
                onSuccess = {
                    biometricEnabled = biometric.enabled()
                    lifecycleScope.launch {
                        vault.refresh()
                        if (vault.unlocked) vault.load()
                    }
                },
                onError = { vault.reportError(it) },
                isSessionCurrent = { vault.uiEpoch == biometricEpoch },
            )
        biometricEnabled = biometric.enabled()
        vault.onCredentialsChanged = {
            biometric.disable()
            biometricEnabled = false
        }
        ContextCompat.registerReceiver(
            this,
            screenOff,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        setContent {
            RedMoonApp(
                vault,
                backup = {
                    vault.run(JSONObject().put("action", "backup")) { response ->
                        vault.pendingBackup =
                            Base64.decode(
                                response.getJSONObject("ok").getString("data"),
                                Base64.NO_WRAP,
                            )
                        backupCoordinator.pending()
                        val stamp =
                            SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(Date())
                        export.launch("RedMoon-$stamp.rm")
                    }
                },
                restore = { importPicker.launch(arrayOf("application/octet-stream", "*/*")) },
                biometricUnlock = {
                    biometricEpoch = vault.uiEpoch
                    biometric.unlock()
                },
                enrollBiometrics = { password ->
                    biometricEpoch = vault.uiEpoch
                    biometric.enroll(password)
                },
                disableBiometrics = {
                    biometric.disable()
                    biometricEnabled = false
                },
                biometricEnabled = biometricEnabled,
                backupStatus = backupStatus,
            )
        }
    }

    private fun secureBackground() {
        if (::biometric.isInitialized) biometric.cancel()
        vault.lock()
    }

    override fun onPause() {
        super.onPause()
        if (!::biometric.isInitialized || !biometric.isAuthenticating) vault.lock()
    }

    override fun onStop() {
        secureBackground()
        super.onStop()
    }

    override fun onDestroy() {
        if (::biometric.isInitialized) biometric.cancel()
        if (::backupCoordinator.isInitialized) backupCoordinator.detach()
        vault.onCredentialsChanged = {}
        unregisterReceiver(screenOff)
        super.onDestroy()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) vault.activity()
        return super.dispatchTouchEvent(event)
    }
}
