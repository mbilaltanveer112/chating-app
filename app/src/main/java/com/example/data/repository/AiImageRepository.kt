package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

enum class AiAspectRatio(val label: String, val ratio: AspectRatio?) {
    SQUARE("1:1", null),
    LANDSCAPE("16:9", AspectRatio.LANDSCAPE_16x9),
    PORTRAIT("3:4", AspectRatio.PORTRAIT_3x4)
}

interface AiImageRepository {
    suspend fun generateImage(prompt: String, aspectRatio: AiAspectRatio = AiAspectRatio.SQUARE): Result<Bitmap>
    suspend fun editImage(inputBitmap: Bitmap, instruction: String, aspectRatio: AiAspectRatio = AiAspectRatio.SQUARE): Result<Bitmap>
}

class FirebaseAiImageRepository : AiImageRepository {

    private val defaultModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    private fun modelForAspect(aspectRatio: AiAspectRatio) =
        if (aspectRatio.ratio == null) {
            defaultModel
        } else {
            Firebase.ai.generativeModel(
                modelName = MODEL_NAME,
                generationConfig = generationConfig {
                    responseModalities = listOf(ResponseModality.IMAGE)
                    imageConfig = ImageConfig.builder()
                        .setAspectRatio(aspectRatio.ratio)
                        .build()
                }
            )
        }

    override suspend fun generateImage(
        prompt: String,
        aspectRatio: AiAspectRatio
    ): Result<Bitmap> {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter a prompt to generate an image."))
        }
        return try {
            val bitmap = withTimeout(GENERATION_TIMEOUT_MS) {
                val response = modelForAspect(aspectRatio).generateContent(cleanPrompt)
                response.candidates.firstOrNull()
                    ?.content?.parts?.filterIsInstance<ImagePart>()
                    ?.firstOrNull()
                    ?.image
            }
            if (bitmap != null) {
                Result.success(bitmap)
            } else {
                Result.failure(IllegalStateException("Gemini did not return an image for this prompt."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image generation timed out", e)
            Result.failure(IllegalStateException("Image generation timed out after 60 seconds. Please try again."))
        } catch (e: QuotaExceededException) {
            Log.e(TAG, "Quota exceeded during image generation", e)
            Result.failure(IllegalStateException("Rate limit or quota exceeded. Please try again shortly."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image generation failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Failed to generate image."))
        }
    }

    override suspend fun editImage(
        inputBitmap: Bitmap,
        instruction: String,
        aspectRatio: AiAspectRatio
    ): Result<Bitmap> {
        val cleanInstruction = instruction.trim()
        if (cleanInstruction.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please describe how you want to edit the image."))
        }
        return try {
            val bitmap = withTimeout(GENERATION_TIMEOUT_MS) {
                val response = modelForAspect(aspectRatio).generateContent(
                    content {
                        image(inputBitmap)
                        text(cleanInstruction)
                    }
                )
                response.candidates.firstOrNull()
                    ?.content?.parts?.filterIsInstance<ImagePart>()
                    ?.firstOrNull()
                    ?.image
            }
            if (bitmap != null) {
                Result.success(bitmap)
            } else {
                Result.failure(IllegalStateException("Gemini did not return an edited image."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image editing timed out", e)
            Result.failure(IllegalStateException("Image editing timed out after 60 seconds. Please try again."))
        } catch (e: QuotaExceededException) {
            Log.e(TAG, "Quota exceeded during image editing", e)
            Result.failure(IllegalStateException("Rate limit or quota exceeded. Please try again shortly."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image editing failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Failed to edit image."))
        }
    }

    private companion object {
        const val TAG = "AiImageRepository"
        const val MODEL_NAME = "gemini-nano-banana-2.1"
        const val GENERATION_TIMEOUT_MS = 60_000L
    }
}
