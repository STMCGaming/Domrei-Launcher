package com.domrei.skinview

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Imperative handle to control a [SkinViewer] (camera, animation restart).
 * Create with [rememberSkinViewerController].
 */
@Stable
class SkinViewerController {
    internal var view: SkinView? = null

    /** Current camera yaw in degrees, or 0 if not attached. */
    val yaw: Float get() = view?.yaw ?: 0f
    val pitch: Float get() = view?.pitch ?: 0f
    val zoom: Float get() = view?.zoom ?: 1f

    fun resetCamera() { view?.resetCamera() }
    fun setCamera(yaw: Float? = null, pitch: Float? = null, zoom: Float? = null) {
        view?.setCamera(yaw, pitch, zoom)
    }
    fun restartAnimation() { view?.restartAnimation() }
}

@Composable
fun rememberSkinViewerController(): SkinViewerController = remember { SkinViewerController() }

/**
 * Interactive 3D Minecraft skin viewer.
 *
 * @param skin skin texture (64x64, legacy 64x32 or HD). `null` shows a generated default skin.
 * @param cape optional cape texture (64x32 or legacy 22x17).
 * @param nameTag optional floating Minecraft nametag above player head.
 * @param config every visual/behavior option; see [SkinViewerConfig].
 * @param controller optional handle for imperative control.
 */
@Composable
fun SkinViewer(
    modifier: Modifier = Modifier,
    skin: Bitmap? = null,
    cape: Bitmap? = null,
    nameTag: String? = null,
    config: SkinViewerConfig = SkinViewerConfig(),
    controller: SkinViewerController? = null,
) {
    val effectiveConfig = remember(config, nameTag) {
        if (nameTag != null) config.copy(nameTag = nameTag) else config
    }
    val defaultSkin = remember { DefaultSkin.create() }
    val holder = remember { arrayOfNulls<SkinView>(1) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder[0]?.onResume()
                Lifecycle.Event.ON_PAUSE -> holder[0]?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SkinView(context, transparent = effectiveConfig.transparentBackground).also {
                it.config = effectiveConfig
                it.setSkin(skin ?: defaultSkin)
                it.setCape(cape)
                holder[0] = it
            }
        },
        update = { view ->
            view.config = effectiveConfig
            view.setSkin(skin ?: defaultSkin)
            view.setCape(cape)
            controller?.view = view
        },
        onRelease = { view ->
            if (controller?.view === view) controller.view = null
            if (holder[0] === view) holder[0] = null
        },
    )
}
