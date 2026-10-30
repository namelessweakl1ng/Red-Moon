package com.redmoon.vault.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Platform-neutral transfer boundary. Only ciphertext from Rust may be passed by the caller. */
object BackupTransfer {
    fun displayName(query: () -> String?): String =
        try {
            query()?.takeIf { it.isNotBlank() } ?: "Encrypted .rm file"
        } catch (_: Exception) {
            "Encrypted .rm file"
        }

    data class Result(val bytes: Int, val verified: Boolean)

    fun write(
        bytes: ByteArray,
        openWrite: () -> OutputStream,
        openRead: () -> InputStream?,
    ): Result {
        require(bytes.isNotEmpty() && bytes.size <= 16 * 1024 * 1024) {
            "Invalid encrypted backup size"
        }
        openWrite().use { output ->
            output.write(bytes)
            output.flush()
        }
        val verified =
            try {
                openRead()?.use { stream ->
                    val readBack = read(stream)
                    MessageDigest.isEqual(
                        MessageDigest.getInstance("SHA-256").digest(bytes),
                        MessageDigest.getInstance("SHA-256").digest(readBack),
                    )
                } ?: false
            } catch (_: Exception) {
                false
            }
        return Result(bytes.size, verified)
    }

    fun read(input: InputStream, limit: Int = 16 * 1024 * 1024): ByteArray {
        require(limit in 1..16 * 1024 * 1024)
        val output = ByteArrayOutputStream(minOf(limit, 8192))
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            require(output.size().toLong() + count <= limit) {
                "Encrypted backup exceeds size limit"
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
