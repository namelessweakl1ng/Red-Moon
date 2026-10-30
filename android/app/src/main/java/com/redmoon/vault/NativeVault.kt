package com.redmoon.vault

import android.os.SystemClock
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch

object NativeVault {
    // Application-lifetime serial queue: locks survive Activity/ViewModel destruction.
    val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val securityScope = CoroutineScope(SupervisorJob() + dispatcher)

    init {
        System.loadLibrary("red_moon_android")
    }

    fun enqueueLock() {
        securityScope.launch {
            try {
                request("{\"action\":\"lock\"}", SystemClock.elapsedRealtime() / 1000)
            } catch (_: Exception) {
                // Initialization also locks before exposing any existing process session.
            }
        }
    }

    @JvmStatic external fun initialize(path: String): String

    @JvmStatic external fun request(request: String, elapsedSeconds: Long): String
}
