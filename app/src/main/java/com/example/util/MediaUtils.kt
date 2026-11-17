package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class DocumentPayload(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val base64Data: String?
)

data class VideoPayload(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val thumbnailBase64: String?
)

object MediaUtils {

    private const val MAX_INLINE_BASE64_BYTES = 240_000

    fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val ratio = min(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
        val targetW = max(1, (width * ratio).toInt())
        val targetH = max(1, (height * ratio).toInt())
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    fun encodeBitmapToBase64(bitmap: Bitmap, maxDimension: Int = 720, quality: Int = 70): String {
        val scaled = scaleBitmap(bitmap, maxDimension)
        var currentQuality = quality
        var bytes: ByteArray
        do {
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, currentQuality, baos)
            bytes = baos.toByteArray()
            currentQuality -= 10
        } while (bytes.size > 260_000 && currentQuality >= 30)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun decodeBase64ToBitmap(base64: String): Bitmap? {
        if (base64.isBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun uriToBitmap(context: Context, uri: Uri, maxDimension: Int = 1024): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val decoded = BitmapFactory.decodeStream(stream) ?: return@withContext null
                    scaleBitmap(decoded, maxDimension)
                }
            } catch (_: Exception) {
                null
            }
        }

    suspend fun readDocumentPayload(context: Context, uri: Uri): DocumentPayload =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            var fileName = "document"
            var sizeBytes = 0L

            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) sizeBytes = cursor.getLong(sizeIdx)
                }
            }

            val rawBytes = resolver.openInputStream(uri)?.use { input ->
                val baos = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buf)
                    if (read == -1) break
                    total += read
                    if (total <= MAX_INLINE_BASE64_BYTES) {
                        baos.write(buf, 0, read)
                    } else {
                        return@use null
                    }
                }
                if (sizeBytes == 0L) sizeBytes = total.toLong()
                baos.toByteArray()
            }

            val base64 = rawBytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
            DocumentPayload(
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                base64Data = base64
            )
        }

    suspend fun readVideoPayload(context: Context, uri: Uri): VideoPayload =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "video/mp4"
            var fileName = "video.mp4"
            var sizeBytes = 0L

            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) sizeBytes = cursor.getLong(sizeIdx)
                }
            }

            var durationMs = 0L
            var thumbBase64: String? = null
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationMs = durStr?.toLongOrNull() ?: 0L
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    thumbBase64 = encodeBitmapToBase64(frame, maxDimension = 560, quality = 68)
                }
            } catch (_: Exception) {
                // Ignore retriever errors on unsupported codecs
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }

            VideoPayload(
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                durationMs = durationMs,
                thumbnailBase64 = thumbBase64
            )
        }

    suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        displayName: String = "PulseChat_${System.currentTimeMillis()}.jpg"
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PulseChat")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create MediaStore entry")

            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            } ?: throw IllegalStateException("Could not open gallery output stream")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        }
    }

    fun openBase64Document(
        context: Context,
        base64: String,
        fileName: String,
        mimeType: String
    ): Result<Unit> = runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "attachment.bin" }
        val file = File(context.cacheDir, safeName)
        FileOutputStream(file).use { it.write(bytes) }
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open $safeName"))
    }

    fun createCameraPhotoUri(context: Context): Uri {
        val file = File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun formatDuration(durationMs: Long): String {
        val totalSeconds = max(0L, durationMs / 1000L)
        val mins = totalSeconds / 60L
        val secs = totalSeconds % 60L
        return String.format(Locale.US, "%d:%02d", mins, secs)
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
            else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}

class VoiceRecorderManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0L

    fun startRecording(): Result<Unit> = runCatching {
        stopAndDiscard()
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        outputFile = file
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        rec.setAudioSamplingRate(16000)
        rec.setAudioEncodingBitRate(24000)
        rec.setOutputFile(file.absolutePath)
        rec.prepare()
        rec.start()
        recorder = rec
        startTimeMs = System.currentTimeMillis()
    }

    fun sampleNormalizedAmplitude(): Int {
        val amp = try {
            recorder?.maxAmplitude ?: 0
        } catch (_: Exception) {
            0
        }
        return ((amp / 32767f) * 100f).toInt().coerceIn(8, 100)
    }

    fun stopAndEncode(): Result<Triple<String, Long, Long>> = runCatching {
        val rec = recorder ?: throw IllegalStateException("Recorder not active")
        val file = outputFile ?: throw IllegalStateException("Missing audio file")
        val elapsedMs = max(500L, System.currentTimeMillis() - startTimeMs)
        try {
            rec.stop()
        } finally {
            rec.release()
            recorder = null
        }
        val bytes = file.readBytes()
        file.delete()
        outputFile = null
        if (bytes.size > 310_000) {
            throw IllegalStateException("Voice note is too long for inline sync. Keep recordings under 90 seconds.")
        }
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        Triple(b64, elapsedMs, bytes.size.toLong())
    }

    fun stopAndDiscard() {
        try {
            recorder?.apply {
                try {
                    stop()
                } catch (_: Exception) {
                }
                release()
            }
        } catch (_: Exception) {
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}

class VoicePlayerManager(private val context: Context) {
    private var player: MediaPlayer? = null
    private var activeMessageId: String? = null

    fun playOrToggle(
        messageId: String,
        base64Audio: String,
        onStateChanged: (playingId: String?, progress: Float, isPlaying: Boolean) -> Unit
    ) {
        val current = player
        if (activeMessageId == messageId && current != null) {
            if (current.isPlaying) {
                current.pause()
                val progress = safeProgress(current)
                onStateChanged(messageId, progress, false)
            } else {
                current.start()
                onStateChanged(messageId, safeProgress(current), true)
            }
            return
        }

        stop()
        if (base64Audio.isBlank()) {
            onStateChanged(null, 0f, false)
            return
        }

        try {
            val bytes = Base64.decode(base64Audio, Base64.DEFAULT)
            val tempFile = File(context.cacheDir, "play_${messageId.take(16)}.m4a")
            FileOutputStream(tempFile).use { it.write(bytes) }
            val mp = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    activeMessageId = null
                    onStateChanged(null, 0f, false)
                    tempFile.delete()
                }
                start()
            }
            player = mp
            activeMessageId = messageId
            onStateChanged(messageId, 0f, true)
        } catch (_: Exception) {
            stop()
            onStateChanged(null, 0f, false)
        }
    }

    fun currentProgress(messageId: String): Pair<Float, Boolean> {
        val current = player ?: return 0f to false
        if (activeMessageId != messageId) return 0f to false
        return safeProgress(current) to try {
            current.isPlaying
        } catch (_: Exception) {
            false
        }
    }

    private fun safeProgress(mp: MediaPlayer): Float {
        return try {
            val dur = mp.duration
            if (dur > 0) (mp.currentPosition.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
        } catch (_: Exception) {
            0f
        }
    }

    fun stop() {
        try {
            player?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {
        }
        player = null
        activeMessageId = null
    }
}
