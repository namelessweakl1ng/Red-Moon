package com.redmoon.vault.security

import android.util.Base64
import org.json.JSONObject

/** Only a KeyStore alias, IV and authenticated ciphertext; never a plaintext vault key. */
internal data class BiometricEnvelope(
    val alias: String,
    val iv: ByteArray,
    val ciphertext: ByteArray,
) {
    fun encode(): ByteArray {
        validate()
        return JSONObject()
            .put("version", 1)
            .put("alias", alias)
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    private fun validate() {
        require(alias.startsWith(ALIAS_PREFIX) && alias.length in 20..100)
        require(iv.size == 12 && ciphertext.size == 48)
    }

    companion object {
        const val ALIAS_PREFIX = "redmoon.biometric."
        val AAD: ByteArray
            get() = "com.redmoon.vault/biometric-envelope/v1".toByteArray(Charsets.UTF_8)

        fun decode(bytes: ByteArray): BiometricEnvelope {
            require(bytes.size in 1..2048)
            try {
                val json = JSONObject(String(bytes, Charsets.UTF_8))
                require(json.getInt("version") == 1)
                return BiometricEnvelope(
                        json.getString("alias"),
                        Base64.decode(json.getString("iv"), Base64.NO_WRAP),
                        Base64.decode(json.getString("ciphertext"), Base64.NO_WRAP),
                    )
                    .also { it.validate() }
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid biometric envelope", e)
            }
        }
    }
}
