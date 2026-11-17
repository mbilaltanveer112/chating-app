package com.example.call

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.util.NotificationHelper

class CallForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, ACTION_UPDATE -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
                val peerName = intent.getStringExtra(EXTRA_PEER_NAME).orEmpty()
                val isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
                val statusText = intent.getStringExtra(EXTRA_STATUS_TEXT).orEmpty()
                promoteToForeground(callId, peerName, isVideo, statusText)
            }
        }
        return START_NOT_STICKY
    }

    private fun promoteToForeground(
        callId: String,
        peerName: String,
        isVideo: Boolean,
        statusText: String
    ) {
        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val hasCamera = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !hasMic) {
            Log.w(TAG, "Skipping CallForegroundService without RECORD_AUDIO permission on API 34+")
            stopSelf()
            return
        }

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            var type = if (hasMic) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            if (isVideo && hasCamera) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            type
        } else {
            0
        }

        val notification = NotificationHelper.buildOngoingCallNotification(
            context = this,
            callId = callId,
            peerName = peerName,
            isVideo = isVideo,
            statusText = statusText
        )

        try {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.ONGOING_CALL_NOTIFICATION_ID,
                notification,
                serviceType
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Unable to start CallForegroundService", e)
            stopSelf()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Foreground service start disallowed from current state", e)
            stopSelf()
        }
    }

    companion object {
        private const val TAG = "CallForegroundService"
        private const val ACTION_START = "com.example.call.FGS_START"
        private const val ACTION_UPDATE = "com.example.call.FGS_UPDATE"
        private const val ACTION_STOP = "com.example.call.FGS_STOP"

        private const val EXTRA_CALL_ID = "extra_call_id"
        private const val EXTRA_PEER_NAME = "extra_peer_name"
        private const val EXTRA_IS_VIDEO = "extra_is_video"
        private const val EXTRA_STATUS_TEXT = "extra_status_text"

        fun start(
            context: Context,
            callId: String,
            peerName: String,
            isVideo: Boolean,
            statusText: String
        ) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_PEER_NAME, peerName)
                putExtra(EXTRA_IS_VIDEO, isVideo)
                putExtra(EXTRA_STATUS_TEXT, statusText)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not start foreground call service", e)
            }
        }

        fun update(
            context: Context,
            callId: String,
            peerName: String,
            isVideo: Boolean,
            statusText: String
        ) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_PEER_NAME, peerName)
                putExtra(EXTRA_IS_VIDEO, isVideo)
                putExtra(EXTRA_STATUS_TEXT, statusText)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.stopService(intent)
            } catch (_: Exception) {
            }
        }
    }
}
