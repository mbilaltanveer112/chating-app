package com.example.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.MainActivity
import com.example.util.NotificationHelper

class CallNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(NotificationHelper.EXTRA_CALL_ID).orEmpty()
        if (callId.isBlank()) return

        val actionName = intent.getStringExtra(NotificationHelper.EXTRA_CALL_ACTION)
        val parsedAction = when (intent.action) {
            ACTION_ACCEPT -> CallActionType.ACCEPT
            ACTION_DECLINE -> CallActionType.DECLINE
            ACTION_HANG_UP -> CallActionType.HANG_UP
            else -> runCatching {
                actionName?.let { CallActionType.valueOf(it) }
            }.getOrNull() ?: CallActionType.OPEN
        }

        if (parsedAction == CallActionType.DECLINE || parsedAction == CallActionType.HANG_UP) {
            NotificationHelper.cancelCallNotifications(context, callId)
        }

        if (parsedAction == CallActionType.ACCEPT || parsedAction == CallActionType.OPEN) {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(NotificationHelper.EXTRA_CALL_ID, callId)
                putExtra(NotificationHelper.EXTRA_CALL_ACTION, parsedAction.name)
            }
            context.startActivity(launchIntent)
        }

        CallEventBus.emit(callId, parsedAction)
    }

    companion object {
        const val ACTION_ACCEPT = "com.example.call.ACTION_ACCEPT"
        const val ACTION_DECLINE = "com.example.call.ACTION_DECLINE"
        const val ACTION_HANG_UP = "com.example.call.ACTION_HANG_UP"
    }
}
