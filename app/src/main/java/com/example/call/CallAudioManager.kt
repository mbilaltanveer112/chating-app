package com.example.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CallAudioManager(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var savedMode: Int = AudioManager.MODE_NORMAL
    private var savedSpeakerOn: Boolean = false
    private var isActiveSession: Boolean = false
    private var focusRequest: AudioFocusRequest? = null

    private var incomingRingtone: Ringtone? = null
    private var ringbackJob: Job? = null
    private var toneGenerator: ToneGenerator? = null

    fun startCallAudio(speakerOn: Boolean) {
        if (!isActiveSession) {
            savedMode = audioManager.mode
            @Suppress("DEPRECATION")
            savedSpeakerOn = audioManager.isSpeakerphoneOn
            isActiveSession = true
        }

        requestAudioFocus()
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        } catch (e: Exception) {
            Log.w(TAG, "Unable to switch audio mode", e)
        }
        setSpeakerphoneOn(speakerOn)
    }

    fun setSpeakerphoneOn(enabled: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (enabled) {
                    val speaker = audioManager.availableCommunicationDevices
                        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speaker != null) {
                        audioManager.setCommunicationDevice(speaker)
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = true
                    }
                } else {
                    val earpiece = audioManager.availableCommunicationDevices
                        .firstOrNull {
                            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                                it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                        }
                    if (earpiece != null) {
                        audioManager.setCommunicationDevice(earpiece)
                    } else {
                        audioManager.clearCommunicationDevice()
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = enabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed setting speakerphone=$enabled", e)
        }
    }

    fun setMicrophoneMute(muted: Boolean) {
        try {
            audioManager.isMicrophoneMute = muted
        } catch (e: Exception) {
            Log.w(TAG, "Failed setting mic mute=$muted", e)
        }
    }

    fun startIncomingRingtone() {
        stopRinging()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(appContext, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && ringtone != null) {
                ringtone.isLooping = true
            }
            ringtone?.play()
            incomingRingtone = ringtone
        } catch (e: Exception) {
            Log.w(TAG, "Unable to play incoming ringtone", e)
        }
    }

    fun startOutgoingRingback(scope: CoroutineScope) {
        stopRinging()
        ringbackJob = scope.launch(Dispatchers.Default) {
            try {
                val generator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 55)
                toneGenerator = generator
                while (isActive) {
                    generator.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1200)
                    delay(3200L)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ringback tone unavailable on this device", e)
            }
        }
    }

    fun stopRinging() {
        ringbackJob?.cancel()
        ringbackJob = null
        try {
            incomingRingtone?.stop()
        } catch (_: Exception) {
        }
        incomingRingtone = null
        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        } catch (_: Exception) {
        }
        toneGenerator = null
    }

    fun releaseCallAudio() {
        stopRinging()
        if (!isActiveSession) return
        isActiveSession = false

        try {
            audioManager.isMicrophoneMute = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = savedSpeakerOn
            }
            audioManager.mode = savedMode
        } catch (e: Exception) {
            Log.w(TAG, "Error restoring audio mode", e)
        }
        abandonAudioFocus()
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { }
                    .build()
                focusRequest = req
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus request failed", e)
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                focusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "CallAudioManager"
    }
}
