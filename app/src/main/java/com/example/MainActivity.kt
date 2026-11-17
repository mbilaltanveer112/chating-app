package com.example

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.example.call.CallActionType
import com.example.call.CallEventBus
import com.example.core.configureAppCheck
import com.example.security.ScreenshotEventBus
import com.example.ui.AppNavigation
import com.example.util.NotificationHelper

object WindowSecurityController {
    fun setFlagSecure(activity: Activity?, enabled: Boolean) {
        val window = activity?.window ?: return
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

class MainActivity : FragmentActivity() {

    private var initialChatIdState by mutableStateOf<String?>(null)
    private var screenCaptureCallback: Any? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory: configure App Check with launch intent before UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        val initialCallId = intent?.getStringExtra(NotificationHelper.EXTRA_CALL_ID)
        val initialCallAction = parseCallAction(intent)
        initialChatIdState = intent?.getStringExtra(NotificationHelper.EXTRA_CHAT_ID)

        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                AppNavigation(
                    initialChatId = initialChatIdState,
                    initialCallId = initialCallId,
                    initialCallAction = initialCallAction
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val callback = Activity.ScreenCaptureCallback {
                ScreenshotEventBus.onScreenCaptured()
            }
            screenCaptureCallback = callback
            runCatching {
                registerScreenCaptureCallback(mainExecutor, callback)
            }
        }
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val callback = screenCaptureCallback as? Activity.ScreenCaptureCallback
            if (callback != null) {
                runCatching {
                    unregisterScreenCaptureCallback(callback)
                }
            }
            screenCaptureCallback = null
        }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val chatId = intent.getStringExtra(NotificationHelper.EXTRA_CHAT_ID)
        if (!chatId.isNullOrBlank()) {
            initialChatIdState = chatId
        }
        val callId = intent.getStringExtra(NotificationHelper.EXTRA_CALL_ID)
        val callAction = parseCallAction(intent)
        if (!callId.isNullOrBlank() && callAction != null) {
            CallEventBus.emit(callId, callAction)
        }
    }

    private fun parseCallAction(intent: Intent?): CallActionType? {
        val raw = intent?.getStringExtra(NotificationHelper.EXTRA_CALL_ACTION) ?: return null
        return runCatching { CallActionType.valueOf(raw) }.getOrNull()
    }
}
