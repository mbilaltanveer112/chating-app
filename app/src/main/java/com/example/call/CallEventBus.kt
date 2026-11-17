package com.example.call

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class CallActionType {
    OPEN,
    ACCEPT,
    DECLINE,
    HANG_UP
}

data class CallNotificationAction(
    val callId: String,
    val action: CallActionType
)

object CallEventBus {
    private val _actions = MutableSharedFlow<CallNotificationAction>(extraBufferCapacity = 8)
    val actions = _actions.asSharedFlow()

    fun emit(callId: String, action: CallActionType) {
        if (callId.isNotBlank()) {
            _actions.tryEmit(CallNotificationAction(callId = callId, action = action))
        }
    }
}
