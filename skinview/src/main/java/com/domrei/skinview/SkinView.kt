package com.domrei.skinview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.TextureView
import com.domrei.skinview.internal.SkinRenderer

/**
 * Hardware-accelerated [TextureView] that renders an interactive 3D Minecraft skin.
 *
 * Uses [TextureView] instead of `GLSurfaceView` so it participates directly in Jetpack Compose
 * and Android View animations (alpha fade in/out, slide transitions, clipping, and Z-ordering)
 * without punching a separate hardware window layer or flickering when panels open/close.
 */
@SuppressLint("ViewConstructor")
class SkinView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    transparent: Boolean = false,
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    private val renderer = SkinRenderer()
    private var currentSkinSource: Bitmap? = null
    private var currentCapeSource: Bitmap? = null
    private var glThread: GLRenderThread? = null
    private var isPausedState = false
    private var continuousRender = true

    /** Current configuration. Setting it applies immediately. */
    var config: SkinViewerConfig = SkinViewerConfig()
        set(value) {
            val old = field
            field = value
            renderer.config = value
            if (old.initialYaw != value.initialYaw ||
                old.initialPitch != value.initialPitch ||
                old.initialZoom != value.initialZoom
            ) {
                resetCamera()
            }
            if (!value.autoRotate && old.autoRotate) {
                // Keep the current orientation when auto-rotation stops.
                renderer.userYaw = renderer.currentYaw
                renderer.resetAutoRotation()
            }
            renderer.zoom = renderer.zoom.coerceIn(value.minZoom, value.maxZoom)
            updateRenderMode()
            requestRender()
        }

    /** Current camera yaw in degrees (including auto-rotation). */
    val yaw: Float get() = renderer.currentYaw
    /** Current camera pitch in degrees. */
    val pitch: Float get() = renderer.userPitch
    /** Current zoom factor. */
    val zoom: Float get() = renderer.zoom

    init {
        isOpaque = !transparent
        surfaceTextureListener = this
        renderer.config = config
        resetCamera()
        updateRenderMode()
    }

    /**
     * Sets the skin texture. Accepts 64x64, legacy 64x32 and HD (multiples of 64) skins.
     * `null` clears the model. Passing the same bitmap instance again is a no-op.
     */
    fun setSkin(skin: Bitmap?) {
        if (skin === currentSkinSource) return
        currentSkinSource = skin
        if (skin == null) {
            renderer.setSkin(null, false)
        } else {
            val normalized = SkinUtils.normalizeSkin(skin)
            renderer.setSkin(normalized, SkinUtils.isSlim(normalized))
        }
        requestRender()
    }

    /** Sets the cape texture (64x32 or legacy 22x17, or multiples). `null` removes the cape. */
    fun setCape(cape: Bitmap?) {
        if (cape === currentCapeSource) return
        currentCapeSource = cape
        renderer.setCape(cape?.let { SkinUtils.normalizeCape(it) })
        requestRender()
    }

    /** Resets yaw/pitch/zoom to the config's initial values. */
    fun resetCamera() {
        renderer.userYaw = config.initialYaw
        renderer.userPitch = config.initialPitch
        renderer.zoom = config.initialZoom.coerceIn(config.minZoom, config.maxZoom)
        renderer.resetAutoRotation()
        requestRender()
    }

    /** Sets the camera directly. `null` keeps the current value. */
    fun setCamera(yaw: Float? = null, pitch: Float? = null, zoom: Float? = null) {
        if (yaw != null) {
            renderer.userYaw = yaw
            renderer.resetAutoRotation()
        }
        if (pitch != null) renderer.userPitch = pitch.coerceIn(-config.maxPitch, config.maxPitch)
        if (zoom != null) renderer.zoom = zoom.coerceIn(config.minZoom, config.maxZoom)
        requestRender()
    }

    /** Restarts the current animation from time 0. */
    fun restartAnimation() {
        renderer.restartAnimation()
        requestRender()
    }

    fun onResume() {
        isPausedState = false
        glThread?.setPaused(false)
    }

    fun onPause() {
        isPausedState = true
        glThread?.setPaused(true)
    }

    fun requestRender() {
        glThread?.requestRender()
    }

    private fun updateRenderMode() {
        val animating = (config.animation != null && !config.animationPaused) || config.autoRotate
        continuousRender = animating
        glThread?.setContinuousRender(animating)
    }

    // ---------------- TextureView.SurfaceTextureListener ----------------

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        glThread?.requestExitAndWait()
        glThread = GLRenderThread(
            surfaceTexture = surface,
            initialWidth = width,
            initialHeight = height,
            renderer = renderer,
            initialContinuous = continuousRender,
            initialPaused = isPausedState,
        ).also { it.start() }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        glThread?.onWindowResize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        glThread?.requestExitAndWait()
        glThread = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    // ---------------- touch ----------------

    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!config.pinchToZoom) return false
                renderer.zoom = (renderer.zoom * detector.scaleFactor).coerceIn(config.minZoom, config.maxZoom)
                requestRender()
                return true
            }
        },
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!config.doubleTapToReset) return false
                resetCamera()
                return true
            }
        },
    )

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cfg = config
        if (!cfg.dragToRotate && !cfg.pinchToZoom && !cfg.doubleTapToReset) return false

        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastX = event.x; lastY = event.y
                dragging = true
            }
            MotionEvent.ACTION_POINTER_DOWN -> dragging = false
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    if (!dragging) {
                        lastX = event.x; lastY = event.y; dragging = true
                    }
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    lastX = event.x; lastY = event.y
                    if (cfg.dragToRotate) {
                        if (cfg.autoRotate) {
                            // fold auto-rotation into user yaw so dragging continues smoothly
                            renderer.userYaw = renderer.currentYaw
                            renderer.resetAutoRotation()
                        }
                        renderer.userYaw += dx * cfg.dragSensitivity
                        if (cfg.allowPitch) {
                            renderer.userPitch = (renderer.userPitch + dy * cfg.dragSensitivity)
                                .coerceIn(-cfg.maxPitch, cfg.maxPitch)
                        }
                        requestRender()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    // ---------------- OpenGL ES 2.0 EGL14 Render Thread ----------------

    private class GLRenderThread(
        private val surfaceTexture: SurfaceTexture,
        initialWidth: Int,
        initialHeight: Int,
        private val renderer: SkinRenderer,
        initialContinuous: Boolean,
        initialPaused: Boolean,
    ) : Thread("SkinViewGLThread") {

        private val lock = Object()
        private var shouldExit = false
        private var exited = false
        private var paused = initialPaused
        private var continuous = initialContinuous
        private var dirty = true
        private var width = initialWidth
        private var height = initialHeight
        private var sizeChanged = true

        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        fun requestRender() {
            synchronized(lock) {
                dirty = true
                lock.notifyAll()
            }
        }

        fun setContinuousRender(value: Boolean) {
            synchronized(lock) {
                continuous = value
                if (value) dirty = true
                lock.notifyAll()
            }
        }

        fun setPaused(value: Boolean) {
            synchronized(lock) {
                paused = value
                if (!value) dirty = true
                lock.notifyAll()
            }
        }

        fun onWindowResize(w: Int, h: Int) {
            synchronized(lock) {
                if (width != w || height != h) {
                    width = w
                    height = h
                    sizeChanged = true
                    dirty = true
                    lock.notifyAll()
                }
            }
        }

        fun requestExitAndWait() {
            synchronized(lock) {
                shouldExit = true
                lock.notifyAll()
                var interrupted = false
                while (!exited && !interrupted) {
                    try {
                        lock.wait(100)
                    } catch (_: InterruptedException) {
                        currentThread().interrupt()
                        interrupted = true
                    }
                }
            }
        }

        override fun run() {
            try {
                if (!initEGL()) return

                renderer.onSurfaceCreated(null, null)

                while (true) {
                    var w = 0
                    var h = 0
                    var notifySize = false
                    var isContinuous = false
                    var exitLoop = false

                    synchronized(lock) {
                        while (!shouldExit && (paused || (!continuous && !dirty && !sizeChanged))) {
                            try {
                                lock.wait()
                            } catch (_: InterruptedException) {
                                currentThread().interrupt()
                            }
                        }
                        if (shouldExit) {
                            exitLoop = true
                        } else {
                            if (sizeChanged) {
                                w = width
                                h = height
                                sizeChanged = false
                                notifySize = true
                            }
                            dirty = false
                            isContinuous = continuous
                        }
                    }
                    if (exitLoop) break

                    if (notifySize && w > 0 && h > 0) {
                        renderer.onSurfaceChanged(null, w, h)
                    }

                    val frameStartMs = System.currentTimeMillis()
                    renderer.onDrawFrame(null)
                    EGL14.eglSwapBuffers(eglDisplay, eglSurface)

                    if (isContinuous) {
                        val elapsed = System.currentTimeMillis() - frameStartMs
                        val waitMs = (16L - elapsed).coerceAtLeast(1L)
                        synchronized(lock) {
                            if (!shouldExit && !dirty && !sizeChanged) {
                                try {
                                    lock.wait(waitMs)
                                } catch (_: InterruptedException) {
                                    currentThread().interrupt()
                                }
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e("SkinView", "GLRenderThread error", t)
            } finally {
                releaseEGL()
                synchronized(lock) {
                    exited = true
                    lock.notifyAll()
                }
            }
        }

        private fun initEGL(): Boolean {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) return false

            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) return false

            val attribList = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 16,
                EGL14.EGL_STENCIL_SIZE, 0,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0) ||
                numConfigs[0] <= 0
            ) {
                return false
            }
            val eglConfig = configs[0] ?: return false

            val ctxAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE,
            )
            eglContext = EGL14.eglCreateContext(
                eglDisplay,
                eglConfig,
                EGL14.EGL_NO_CONTEXT,
                ctxAttribs,
                0,
            )
            if (eglContext == EGL14.EGL_NO_CONTEXT) return false

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(
                eglDisplay,
                eglConfig,
                surfaceTexture,
                surfaceAttribs,
                0,
            )
            if (eglSurface == EGL14.EGL_NO_SURFACE) return false

            return EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        private fun releaseEGL() {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    eglDisplay,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT,
                )
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    eglSurface = EGL14.EGL_NO_SURFACE
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                    eglContext = EGL14.EGL_NO_CONTEXT
                }
                EGL14.eglTerminate(eglDisplay)
                eglDisplay = EGL14.EGL_NO_DISPLAY
            }
        }
    }
}
