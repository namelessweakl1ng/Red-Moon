package com.redmoon.vault.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import android.util.AtomicFile
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Auth-per-use biometric wrapping of a derived vault key. No master password is persisted.
 * AndroidKeyStore uses secure hardware when the device implementation supports it; this class does
 * not claim a hardware security level or fall back to device credentials.
 *
 * All entry points run on the main thread. The caller MUST cancel on actual background, screen-off,
 * vault lock, password change and restore, and check its captured session epoch in isSessionCurrent
 * AND inside the native callbacks before dispatch. Do not blindly cancel in onPause: an OEM's
 * biometric prompt can pause its host. onStop must cancel. Export runs only after a successful
 * encryption CryptoObject prompt and must reauthenticate the master password in the native core.
 * Unlock must authenticate the current vault there. JVM password/JNI Base64 Strings cannot be
 * reliably wiped; byte buffers are wiped below.
 */
class BiometricVault(
    private val activity: FragmentActivity,
    private val exportKey: suspend (String) -> ByteArray,
    private val unlockKey: suspend (ByteArray) -> Unit,
    private val onSuccess: () -> Unit,
    private val onError: (String) -> Unit,
    private val isSessionCurrent: () -> Boolean,
) {
    private val store = AtomicFile(File(activity.noBackupFilesDir, "biometric-key-v1.json"))
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private var generation = 0L
    private var prompt: BiometricPrompt? = null
    private var job: Job? = null
    private var pendingAlias: String? = null
    private var pendingPassword: String? = null
    var isAuthenticating: Boolean = false
        private set

    fun available(): Boolean =
        BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun enabled(): Boolean =
        try {
            val envelope = readEnvelope()
            keyStore.containsAlias(envelope.alias)
        } catch (_: Exception) {
            false
        }

    fun enroll(password: String) {
        cancel()
        if (!available()) {
            fail()
            return
        }
        try {
            val alias = BiometricEnvelope.ALIAS_PREFIX + UUID.randomUUID()
            pendingAlias = alias
            val generator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec =
                KeyGenParameterSpec.Builder(
                        alias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(true)
                    .setInvalidatedByBiometricEnrollment(true)
            if (Build.VERSION.SDK_INT >= 30)
                spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            else spec.setUserAuthenticationValidityDurationSeconds(-1)
            generator.init(spec.build())
            val key = generator.generateKey()
            val cipher =
                Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.ENCRYPT_MODE, key)
                    updateAAD(BiometricEnvelope.AAD)
                }
            pendingPassword = password
            authenticate(cipher, enrolling = true, alias = alias)
        } catch (_: Exception) {
            cleanupPending()
            fail()
        }
    }

    fun unlock() {
        cancel()
        if (!available()) {
            fail()
            return
        }
        try {
            val envelope = readEnvelope()
            val key = keyStore.getKey(envelope.alias, null) as SecretKey
            val cipher =
                Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, envelope.iv))
                    updateAAD(BiometricEnvelope.AAD)
                }
            authenticate(
                cipher,
                enrolling = false,
                alias = envelope.alias,
                wrapped = envelope.ciphertext,
            )
        } catch (_: Exception) {
            fail()
        }
    }

    fun disable() {
        cancel()
        try {
            store.delete()
            // Includes orphaned keys left by interrupted enrollment, only in our namespace.
            val aliases =
                keyStore.aliases().toList().filter { it.startsWith(BiometricEnvelope.ALIAS_PREFIX) }
            aliases.forEach { keyStore.deleteEntry(it) }
            onSuccess()
        } catch (_: Exception) {
            fail()
        }
    }

    fun cancel() {
        generation++
        isAuthenticating = false
        prompt?.cancelAuthentication()
        prompt = null
        job?.cancel()
        job = null
        cleanupPending()
    }

    private fun authenticate(
        cipher: Cipher,
        enrolling: Boolean,
        alias: String,
        wrapped: ByteArray? = null,
    ) {
        val token = generation
        isAuthenticating = true
        var terminal = false
        val callback =
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (token != generation || terminal) return
                    terminal = true
                    isAuthenticating = false
                    cleanupPending()
                    fail()
                }

                override fun onAuthenticationFailed() {
                    // The prompt remains open for another scan. No vault operation has run.
                }

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    if (token != generation || terminal) return
                    terminal = true
                    isAuthenticating = false
                    if (!current(token)) {
                        cleanupPending()
                        return
                    }
                    val authenticated =
                        result.cryptoObject?.cipher
                            ?: run {
                                cleanupPending()
                                fail()
                                return
                            }
                    if (authenticated !== cipher) {
                        cleanupPending()
                        fail()
                        return
                    }
                    val password = pendingPassword
                    pendingPassword = null
                    job =
                        activity.lifecycleScope.launch {
                            var material: ByteArray? = null
                            try {
                                if (!current(token)) return@launch
                                material =
                                    if (enrolling) exportKey(requireNotNull(password))
                                    else authenticated.doFinal(requireNotNull(wrapped))
                                require(material.size == 32)
                                if (!current(token)) return@launch
                                if (enrolling) {
                                    val encrypted = authenticated.doFinal(material)
                                    val previous =
                                        try {
                                            readEnvelope().alias
                                        } catch (_: Exception) {
                                            null
                                        }
                                    writeEnvelope(
                                        BiometricEnvelope(alias, authenticated.iv, encrypted)
                                    )
                                    pendingAlias = null
                                    if (previous != null && previous != alias)
                                        runCatching { keyStore.deleteEntry(previous) }
                                } else {
                                    unlockKey(material)
                                }
                                if (current(token)) onSuccess()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                if (current(token)) fail()
                            } finally {
                                material?.fill(0)
                                if (token == generation) cleanupPending()
                            }
                        }
                }
            }
        prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val info =
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(if (enrolling) "Enable biometric unlock" else "Unlock Red Moon")
                .setSubtitle("Authenticate with a strong biometric")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Use master password")
                .build()
        prompt!!.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun current(token: Long): Boolean =
        token == generation &&
            isSessionCurrent() &&
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun cleanupPending() {
        pendingPassword = null
        pendingAlias?.let { runCatching { keyStore.deleteEntry(it) } }
        pendingAlias = null
    }

    private fun fail() {
        isAuthenticating = false
        onError(
            "Biometric unlock is unavailable or was cancelled. Use your master password. You can enable biometrics again after unlocking."
        )
    }

    private fun readEnvelope(): BiometricEnvelope =
        store.openRead().use {
            // Bound reads even if a corrupted file is unexpectedly large.
            val bytes = ByteArray(2049)
            var count = 0
            while (count < bytes.size) {
                val read = it.read(bytes, count, bytes.size - count)
                if (read < 0) break
                count += read
            }
            BiometricEnvelope.decode(bytes.copyOf(count))
        }

    private fun writeEnvelope(value: BiometricEnvelope) {
        val bytes = value.encode()
        val output = store.startWrite()
        try {
            Os.fchmod(output.fd, 0x180) // 0600, including AtomicFile's temporary inode.
            output.write(bytes)
            store.finishWrite(output)
        } catch (e: Exception) {
            store.failWrite(output)
            throw e
        }
    }
}
