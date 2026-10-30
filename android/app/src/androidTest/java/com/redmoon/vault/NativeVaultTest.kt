package com.redmoon.vault

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeVaultTest {
    @Test
    fun realJniAuthenticatesAndLocksWithoutNetworkPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory =
            java.io.File(context.cacheDir, "test-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val initialized =
            JSONObject(NativeVault.initialize(java.io.File(directory, "vault.rm").absolutePath))
        assertFalse(initialized.has("error"))
        fun call(request: String) =
            JSONObject(
                NativeVault.request(request, android.os.SystemClock.elapsedRealtime() / 1000)
            )
        assertFalse(
            call("""{"action":"create","password":"long instrumentation passphrase"}""")
                .has("error")
        )
        assertTrue(
            call("""{"action":"list"}""").getJSONObject("ok").getJSONArray("entries").length() == 0
        )
        call("""{"action":"lock"}""")
        assertTrue(call("""{"action":"list"}""").has("error"))
        assertTrue(call("""{"action":"unlock","password":"wrong"}""").has("error"))
        assertFalse(
            call("""{"action":"unlock","password":"long instrumentation passphrase"}""")
                .has("error")
        )
        call("""{"action":"lock"}""")
        val info =
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_PERMISSIONS,
            )
        assertFalse(info.requestedPermissions?.contains("android.permission.INTERNET") ?: false)
    }
}
