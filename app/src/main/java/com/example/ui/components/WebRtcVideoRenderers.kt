package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@Composable
fun RemoteVideoRendererView(
    eglBaseContext: EglBase.Context?,
    onAttach: (SurfaceViewRenderer) -> Unit,
    onDetach: (SurfaceViewRenderer) -> Unit,
    modifier: Modifier = Modifier
) {
    if (eglBaseContext == null) return
    val context = LocalContext.current
    var initialized by remember { mutableStateOf(false) }

    val renderer = remember(context, eglBaseContext) {
        SurfaceViewRenderer(context).apply {
            runCatching {
                init(eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                setMirror(false)
                initialized = true
            }
        }
    }

    DisposableEffect(renderer) {
        if (initialized) {
            onAttach(renderer)
        }
        onDispose {
            onDetach(renderer)
            runCatching { renderer.release() }
        }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier
    )
}

@Composable
fun LocalVideoRendererView(
    eglBaseContext: EglBase.Context?,
    isFrontCamera: Boolean,
    isPiP: Boolean,
    onAttach: (SurfaceViewRenderer) -> Unit,
    onDetach: (SurfaceViewRenderer) -> Unit,
    modifier: Modifier = Modifier
) {
    if (eglBaseContext == null) return
    val context = LocalContext.current
    var initialized by remember { mutableStateOf(false) }

    val renderer = remember(context, eglBaseContext) {
        SurfaceViewRenderer(context).apply {
            runCatching {
                init(eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                setZOrderMediaOverlay(isPiP)
                setMirror(isFrontCamera)
                initialized = true
            }
        }
    }

    LaunchedEffect(renderer, isFrontCamera) {
        if (initialized) {
            runCatching { renderer.setMirror(isFrontCamera) }
        }
    }

    DisposableEffect(renderer) {
        if (initialized) {
            onAttach(renderer)
        }
        onDispose {
            onDetach(renderer)
            runCatching { renderer.release() }
        }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier
    )
}
