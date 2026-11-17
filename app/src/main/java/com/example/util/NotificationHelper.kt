package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.R
import com.example.call.CallActionType
import com.example.call.CallNotificationActionReceiver
import com.example.data.model.CallSession

object NotificationHelper {

    private const val CHANNEL_ID = "pulsechat_messages"
    private const val CHANNEL_NAME = "Messages"

    const val CHANNEL_INCOMING_CALLS = "pulsechat_incoming_calls"
    private const val CHANNEL_INCOMING_CALLS_NAME = "Incoming Calls"

    const val CHANNEL_ONGOING_CALLS = "pulsechat_ongoing_calls"
    private const val CHANNEL_ONGOING_CALLS_NAME = "Active Calls"

    const val EXTRA_CHAT_ID = "chatId"
    const val EXTRA_CALL_ID = "callId"
    const val EXTRA_CALL_ACTION = "callAction"

    const val ONGOING_CALL_NOTIFICATION_ID = 42001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Real-time incoming chat message alerts"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun ensureCallChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (manager.getNotificationChannel(CHANNEL_INCOMING_CALLS) == null) {
                val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build()
                val incomingChannel = NotificationChannel(
                    CHANNEL_INCOMING_CALLS,
                    CHANNEL_INCOMING_CALLS_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Incoming voice and video calls"
                    enableVibration(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    if (ringtoneUri != null) {
                        setSound(ringtoneUri, audioAttributes)
                    }
                }
                manager.createNotificationChannel(incomingChannel)
            }

            if (manager.getNotificationChannel(CHANNEL_ONGOING_CALLS) == null) {
                val ongoingChannel = NotificationChannel(
                    CHANNEL_ONGOING_CALLS,
                    CHANNEL_ONGOING_CALLS_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Active voice and video call status"
                    setSound(null, null)
                    enableVibration(false)
                }
                manager.createNotificationChannel(ongoingChannel)
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun showIncomingMessageNotification(
        context: Context,
        chatId: String,
        senderName: String,
        preview: String,
        isLockedChat: Boolean = false
    ) {
        if (!hasNotificationPermission(context)) return

        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CHAT_ID, chatId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isLockedChat) "Locked Chat" else senderName.ifBlank { "New Message" }
        val body = if (isLockedChat) "New private message" else preview.ifBlank { "Sent you a message" }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (isLockedChat) {
            builder.setVisibility(NotificationCompat.VISIBILITY_SECRET)
        }

        try {
            NotificationManagerCompat.from(context).notify(chatId.hashCode(), builder.build())
        } catch (_: SecurityException) {
            // Permission revoked
        }
    }

    fun showScreenshotAlertNotification(
        context: Context,
        chatId: String,
        actorName: String,
        formattedTime: String,
        isLockedChat: Boolean = false
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CHAT_ID, chatId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (chatId + "_ss").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isLockedChat) "Locked Chat Security Alert" else "Screenshot Detected"
        val body = if (isLockedChat) {
            "A screenshot event was recorded in a locked conversation."
        } else {
            "$actorName took a screenshot ($formattedTime)"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(
                if (isLockedChat) NotificationCompat.VISIBILITY_SECRET
                else NotificationCompat.VISIBILITY_PRIVATE
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                (chatId + "_screenshot").hashCode(),
                notification
            )
        } catch (_: SecurityException) {
            // Permission revoked
        }
    }

    fun showIncomingCallNotification(
        context: Context,
        call: CallSession
    ) {
        if (!hasNotificationPermission(context)) return
        ensureCallChannels(context)

        val callerName = call.callerName.ifBlank { "PulseChat User" }
        val callerPerson = buildPerson(callerName, call.callerAvatarData)

        val openIntent = PendingIntent.getActivity(
            context,
            requestCodeFor(call.id, 1),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_CALL_ID, call.id)
                putExtra(EXTRA_CALL_ACTION, CallActionType.OPEN.name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerIntent = PendingIntent.getActivity(
            context,
            requestCodeFor(call.id, 2),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_CALL_ID, call.id)
                putExtra(EXTRA_CALL_ACTION, CallActionType.ACCEPT.name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = PendingIntent.getBroadcast(
            context,
            requestCodeFor(call.id, 3),
            Intent(context, CallNotificationActionReceiver::class.java).apply {
                action = CallNotificationActionReceiver.ACTION_DECLINE
                putExtra(EXTRA_CALL_ID, call.id)
                putExtra(EXTRA_CALL_ACTION, CallActionType.DECLINE.name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callStyle = NotificationCompat.CallStyle.forIncomingCall(
            callerPerson,
            declineIntent,
            answerIntent
        ).setIsVideo(call.isVideo)

        val notification = NotificationCompat.Builder(context, CHANNEL_INCOMING_CALLS)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(callerName)
            .setContentText(if (call.isVideo) "Incoming video call" else "Incoming voice call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(openIntent, true)
            .setContentIntent(openIntent)
            .setStyle(callStyle)
            .addPerson(callerPerson)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(incomingCallNotificationId(call.id), notification)
        } catch (_: SecurityException) {
            // Permission revoked
        }
    }

    fun buildOngoingCallNotification(
        context: Context,
        callId: String,
        peerName: String,
        isVideo: Boolean,
        statusText: String
    ): android.app.Notification {
        ensureCallChannels(context)
        val cleanName = peerName.ifBlank { "PulseChat Call" }
        val peerPerson = Person.Builder().setName(cleanName).setImportant(true).build()

        val openIntent = PendingIntent.getActivity(
            context,
            requestCodeFor(callId, 11),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_CALL_ACTION, CallActionType.OPEN.name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangUpIntent = PendingIntent.getBroadcast(
            context,
            requestCodeFor(callId, 12),
            Intent(context, CallNotificationActionReceiver::class.java).apply {
                action = CallNotificationActionReceiver.ACTION_HANG_UP
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_CALL_ACTION, CallActionType.HANG_UP.name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callStyle = NotificationCompat.CallStyle.forOngoingCall(peerPerson, hangUpIntent)
            .setIsVideo(isVideo)

        return NotificationCompat.Builder(context, CHANNEL_ONGOING_CALLS)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(cleanName)
            .setContentText(statusText.ifBlank { if (isVideo) "Video call in progress" else "Voice call in progress" })
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .setStyle(callStyle)
            .addPerson(peerPerson)
            .build()
    }

    fun showMissedCallNotification(
        context: Context,
        call: CallSession
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val callerName = call.callerName.ifBlank { "Contact" }
        val callLabel = if (call.isVideo) "Missed video call" else "Missed voice call"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (call.chatId.isNotBlank()) {
                putExtra(EXTRA_CHAT_ID, call.chatId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCodeFor(call.id, 21),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(callLabel)
            .setContentText(callerName)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(missedCallNotificationId(call.id), notification)
        } catch (_: SecurityException) {
            // Permission revoked
        }
    }

    fun cancelCallNotifications(context: Context, callId: String) {
        if (callId.isBlank()) return
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(incomingCallNotificationId(callId))
        manager.cancel(ONGOING_CALL_NOTIFICATION_ID)
    }

    fun incomingCallNotificationId(callId: String): Int =
        10_000 + (callId.hashCode() and 0x7FFF)

    private fun missedCallNotificationId(callId: String): Int =
        50_000 + (callId.hashCode() and 0x7FFF)

    private fun requestCodeFor(callId: String, salt: Int): Int =
        ((callId.hashCode() * 31) + salt) and 0x7FFFFFFF

    private fun buildPerson(name: String, avatarData: String): Person {
        val builder = Person.Builder()
            .setName(name)
            .setImportant(true)
        if (avatarData.isNotBlank()) {
            val decoded = MediaUtils.decodeBase64ToBitmap(avatarData)
            if (decoded != null) {
                val circular = toCircularBitmap(decoded)
                builder.setIcon(IconCompat.createWithBitmap(circular))
            }
        }
        return builder.build()
    }

    private fun toCircularBitmap(source: Bitmap): Bitmap {
        val size = minOf(source.width, source.height).coerceIn(64, 256)
        val scaled = Bitmap.createScaledBitmap(source, size, size, true)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, size, size)
        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaled, rect, rect, paint)
        return output
    }
}
