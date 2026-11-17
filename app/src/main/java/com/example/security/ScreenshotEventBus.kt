package com.example.security

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class ScreenshotCaptureEvent(
    val timestampMs: Long
)

/**
 * Deduplicates rapid OS callbacks from Activity.ScreenCaptureCallback (API 34+)
 * so a single screenshot gesture does not emit duplicate system messages or notifications.
 */
object ScreenshotEventBus {
    private const val DEDUP_WINDOW_MS = 2_500L

    private val _events = MutableSharedFlow<ScreenshotCaptureEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ScreenshotCaptureEvent> = _events.asSharedFlow()

    @Volatile
    private var lastEmittedMs: Long = 0L

    fun onScreenCaptured(nowMs: Long = System.currentTimeMillis()): Boolean {
        synchronized(this) {
            if (nowMs - lastEmittedMs < DEDUP_WINDOW_MS) {
                return false
            }
            lastEmittedMs = nowMs
        }
        return _events.tryEmit(ScreenshotCaptureEvent(timestampMs = nowMs))
    }
}
