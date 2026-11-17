package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ChatRoom
import com.example.data.repository.AiAspectRatio
import com.example.ui.components.ChatPickerBottomSheet
import com.example.ui.viewmodel.AiStudioUiState
import com.example.util.MediaUtils
import kotlinx.coroutines.launch

private val CREATE_PRESETS = listOf(
    "Neon cyberpunk skyline at dusk, ultra detailed",
    "Minimalist 3D clay mascot waving hello, studio lighting",
    "Watercolor botanical garden with glowing fireflies",
    "Retro synthwave sunset over calm ocean waves"
)

private val EDIT_PRESETS = listOf(
    "Transform into a vibrant anime illustration",
    "Add dramatic golden hour sunset lighting",
    "Turn into a clean minimalist vector sticker",
    "Replace background with a starry cosmic nebula"
)

@Composable
fun AiStudioScreen(
    currentUserId: String,
    aiUiState: AiStudioUiState,
    sourceBitmap: Bitmap?,
    suggestedPrompt: String,
    chats: List<ChatRoom>,
    onSourceBitmapChanged: (Bitmap?) -> Unit,
    onGenerate: (String, AiAspectRatio) -> Unit,
    onEdit: (Bitmap, String, AiAspectRatio) -> Unit,
    onSendToChat: (ChatRoom, Bitmap, String) -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var modeIndex by remember(sourceBitmap) {
        mutableIntStateOf(if (sourceBitmap != null) 1 else 0)
    }
    var prompt by remember { mutableStateOf(suggestedPrompt) }
    var aspectRatio by remember { mutableStateOf(AiAspectRatio.SQUARE) }
    var showSendChatPicker by remember { mutableStateOf(false) }

    LaunchedEffect(suggestedPrompt) {
        if (suggestedPrompt.isNotBlank()) {
            prompt = suggestedPrompt
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = MediaUtils.uriToBitmap(context, uri, maxDimension = 1024)
                if (bmp != null) {
                    onSourceBitmapChanged(bmp)
                    modeIndex = 1
                } else {
                    onNotify("Could not load selected photo")
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "AI Studio",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Column {
                Text(
                    text = "AI Image Studio",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Create or remix images with gemini-nano-banana-2.1",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Create vs Edit mode switcher
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = modeIndex == 0,
                onClick = { modeIndex = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) }
            ) {
                Text("Create Image")
            }
            SegmentedButton(
                selected = modeIndex == 1,
                onClick = { modeIndex = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
            ) {
                Text("Edit Image")
            }
        }

        // Source Image picker in Edit mode
        if (modeIndex == 1) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                if (sourceBitmap != null) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            bitmap = sourceBitmap.asImageBitmap(),
                            contentDescription = "Source image for AI editing",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(20.dp))
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            ) {
                                IconButton(
                                    onClick = {
                                        photoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                ) {
                                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = "Change source photo")
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            ) {
                                IconButton(onClick = { onSourceBitmapChanged(null) }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove source photo")
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = "Pick photo to edit",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = "Select a photo to edit with AI",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Uses Android Photo Picker — zero storage permissions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Prompt Input
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = {
                Text(if (modeIndex == 0) "Describe the image to create" else "Describe the edit to apply")
            },
            placeholder = {
                Text(
                    if (modeIndex == 0) "e.g., Cozy mountain cabin in glowing northern lights"
                    else "e.g., Add soft cinematic rim lighting and watercolor texture"
                )
            },
            minLines = 3,
            maxLines = 5,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_prompt_input")
        )

        // Quick Prompt Presets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = if (modeIndex == 0) CREATE_PRESETS else EDIT_PRESETS
            presets.forEach { preset ->
                SuggestionChip(
                    onClick = { prompt = preset },
                    label = { Text(preset, maxLines = 1) }
                )
            }
        }

        // Aspect Ratio Selector
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Ratio:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AiAspectRatio.entries.forEach { option ->
                FilterChip(
                    selected = aspectRatio == option,
                    onClick = { aspectRatio = option },
                    label = { Text(option.label) }
                )
            }
        }

        // Primary Action Button
        val isLoading = aiUiState is AiStudioUiState.Loading
        Button(
            onClick = {
                if (modeIndex == 0) {
                    onGenerate(prompt, aspectRatio)
                } else {
                    val src = sourceBitmap
                    if (src == null) {
                        onNotify("Please pick an image to edit first")
                    } else {
                        onEdit(src, prompt, aspectRatio)
                    }
                }
            },
            enabled = !isLoading && prompt.isNotBlank() && (modeIndex == 0 || sourceBitmap != null),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("ai_generate_button")
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(if (modeIndex == 0) "Creating image..." else "Applying AI edit...")
            } else {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (modeIndex == 0) "Generate Image" else "Apply AI Edit")
            }
        }

        // Result / Loading / Error Canvas
        when (aiUiState) {
            is AiStudioUiState.Idle -> {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                        Text(
                            text = "Your generated artwork appears here",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Send AI creations straight into any PulseChat conversation or save to your gallery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is AiStudioUiState.Loading -> {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = if (aiUiState.isEditing) {
                                    "Editing image with gemini-nano-banana-2.1..."
                                } else {
                                    "Synthesizing image with gemini-nano-banana-2.1..."
                                },
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Image synthesis typically takes 15–45 seconds.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            is AiStudioUiState.Error -> {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = aiUiState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is AiStudioUiState.Success -> {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            bitmap = aiUiState.bitmap.asImageBitmap(),
                            contentDescription = "AI result for ${aiUiState.prompt}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        MediaUtils.saveBitmapToGallery(context, aiUiState.bitmap)
                                            .onSuccess { onNotify("Saved to Photos/PulseChat") }
                                            .onFailure { onNotify(it.localizedMessage ?: "Save failed") }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save")
                            }
                            OutlinedButton(
                                onClick = {
                                    onSourceBitmapChanged(aiUiState.bitmap)
                                    modeIndex = 1
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Remix")
                            }
                        }
                        Button(
                            onClick = { showSendChatPicker = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("ai_send_to_chat_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Send to Chat")
                        }
                    }
                }
            }
        }
    }

    if (showSendChatPicker && aiUiState is AiStudioUiState.Success) {
        ChatPickerBottomSheet(
            title = "Send AI image to...",
            chats = chats,
            currentUserId = currentUserId,
            onSelectChat = { selectedRoom ->
                showSendChatPicker = false
                onSendToChat(selectedRoom, aiUiState.bitmap, aiUiState.prompt)
            },
            onDismiss = { showSendChatPicker = false }
        )
    }
}
