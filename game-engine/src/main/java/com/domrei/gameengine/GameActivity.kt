package com.domrei.gameengine

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.domrei.gameengine.runtime.DomreiJliRunner
import com.domrei.gameengine.runtime.DomreiJvmService
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.concurrent.thread

/**
 * Full-screen Android Activity running in the `:jvm` process that hosts the native
 * Minecraft `SurfaceView` (`ANativeWindow*`) and launches OpenJDK via [DomreiJliRunner].
 */
class GameActivity : Activity(), SurfaceHolder.Callback {

    private lateinit var surfaceView: SurfaceView
    private lateinit var controlsContainer: FrameLayout
    private lateinit var logOverlayContainer: LinearLayout
    private lateinit var logScrollView: ScrollView
    private lateinit var logTextView: TextView
    private lateinit var statusBadgeView: TextView
    private lateinit var toggleLogsButton: TextView
    private lateinit var toggleControlsButton: TextView

    private val mainHandler = Handler(Looper.getMainLooper())
    private var specFile: File? = null
    private var logFile: File? = null

    @Volatile
    private var jvmStarted = false

    @Volatile
    private var activeNativeWindowPtr: Long = 0L

    @Volatile
    private var logPollingActive = false

    private var logsVisible = true
    private var controlsVisible = true
    private var autoHiddenLogsOnGlCreated = false
    private var inputMmap: MappedByteBuffer? = null
    private val inputLock = Any()

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var virtualLookX = 960f
    private var virtualLookY = 540f
    private var sneakToggled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val specPath = intent?.getStringExtra(DomreiJvmService.EXTRA_SPEC_FILE_PATH)
        val logPath = intent?.getStringExtra(DomreiJvmService.EXTRA_LOG_FILE_PATH)
        if (specPath.isNullOrBlank() || logPath.isNullOrBlank()) {
            finish()
            return
        }
        specFile = File(specPath)
        logFile = File(logPath)
        initInputBridgeBuffer()

        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        // 1. Fullscreen SurfaceView for Minecraft OpenGL/EGL rendering + touch-to-mouse forwarding
        surfaceView = SurfaceView(this).apply {
            holder.addCallback(this@GameActivity)
            isFocusable = true
            isFocusableInTouchMode = true
            setOnTouchListener { _, event ->
                handleSurfaceTouchEvent(event)
                true
            }
        }
        rootLayout.addView(
            surfaceView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // 1b. On-screen touch controls overlay (WASD + Sneak + Jump + PRI/SEC + INV)
        controlsContainer = buildTouchControlsOverlay()
        rootLayout.addView(
            controlsContainer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // 2. Collapsible translucent live log overlay
        logOverlayContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.argb(185, 10, 14, 18))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.argb(70, 56, 239, 125))
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        val logHeaderTitle = TextView(this).apply {
            text = "Domrei Game Engine — Live Console"
            setTextColor(Color.rgb(56, 239, 125))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
        }
        logOverlayContainer.addView(logHeaderTitle)

        logScrollView = ScrollView(this).apply {
            isFillViewport = true
        }
        logTextView = TextView(this).apply {
            setTextColor(Color.argb(235, 230, 238, 245))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.MONOSPACE
            text = "Waiting for Minecraft surface..."
        }
        logScrollView.addView(logTextView)
        logOverlayContainer.addView(
            logScrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                topMargin = dp(6)
            }
        )

        rootLayout.addView(
            logOverlayContainer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(dp(18), dp(54), dp(18), dp(18))
            }
        )

        // 3. Top HUD bar (Status + Toggle Logs + Exit Game)
        val topHudBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.argb(190, 14, 19, 24))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.argb(80, 255, 255, 255))
            }
            setPadding(dp(12), dp(6), dp(8), dp(6))
        }

        statusBadgeView = TextView(this).apply {
            text = "Starting JVM..."
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
        }
        topHudBar.addView(statusBadgeView)

        val f3Button = TextView(this).apply {
            text = "F3"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.argb(190, 60, 72, 88))
                cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener {
                // GLFW_KEY_F3 = 292
                tapGlfwKey(292)
            }
        }
        topHudBar.addView(
            f3Button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(8)
            }
        )

        val f5Button = TextView(this).apply {
            text = "F5"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.argb(190, 60, 72, 88))
                cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener {
                // GLFW_KEY_F5 = 294
                tapGlfwKey(294)
            }
        }
        topHudBar.addView(
            f5Button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(6)
            }
        )

        val escButton = TextView(this).apply {
            text = "ESC"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.argb(190, 60, 72, 88))
                cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener {
                // GLFW_KEY_ESCAPE = 256
                tapGlfwKey(256)
            }
        }
        topHudBar.addView(
            escButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(6)
            }
        )

        toggleControlsButton = TextView(this).apply {
            text = "Hide Pad"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.argb(190, 60, 72, 88))
                cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener {
                controlsVisible = !controlsVisible
                controlsContainer.visibility = if (controlsVisible) View.VISIBLE else View.GONE
                toggleControlsButton.text = if (controlsVisible) "Hide Pad" else "Show Pad"
            }
        }
        topHudBar.addView(
            toggleControlsButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(6)
            }
        )

        toggleLogsButton = TextView(this).apply {
            text = "Hide Logs"
            setTextColor(Color.rgb(12, 16, 20))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.rgb(56, 239, 125))
                cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener {
                setLogsOverlayVisible(!logsVisible)
            }
        }
        topHudBar.addView(
            toggleLogsButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(6)
            }
        )

        val exitButton = TextView(this).apply {
            text = "Exit"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.argb(210, 220, 53, 69))
                cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener {
                finishAndKillJvm()
            }
        }
        topHudBar.addView(
            exitButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(6)
            }
        )

        rootLayout.addView(
            topHudBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                setMargins(dp(16), dp(10), dp(18), dp(10))
            }
        )

        setContentView(rootLayout)
        enableImmersiveFullscreen()
        startLogTailer()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveFullscreen()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val surface = holder.surface
        val winPtr = DomreiJliRunner.acquireNativeWindowPointer(surface)
        activeNativeWindowPtr = winPtr

        if (!jvmStarted) {
            jvmStarted = true
            val surfaceW = surfaceView.width.takeIf { it > 0 } ?: 1920
            val surfaceH = surfaceView.height.takeIf { it > 0 } ?: 1080
            startMinecraftJvmThread(winPtr, surfaceW, surfaceH)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (width > 0 && height > 0) {
            System.setProperty("glfwstub.windowWidth", width.toString())
            System.setProperty("glfwstub.windowHeight", height.toString())
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        val ptr = activeNativeWindowPtr
        activeNativeWindowPtr = 0L
        if (ptr != 0L) {
            DomreiJliRunner.releaseNativeWindowPointer(ptr)
        }
    }

    override fun onDestroy() {
        logPollingActive = false
        val ptr = activeNativeWindowPtr
        activeNativeWindowPtr = 0L
        if (ptr != 0L) {
            DomreiJliRunner.releaseNativeWindowPointer(ptr)
        }
        super.onDestroy()
    }

    private fun startMinecraftJvmThread(nativeWindowPtr: Long, surfaceWidth: Int, surfaceHeight: Int) {
        val currentSpecFile = specFile ?: return
        val currentLogFile = logFile ?: return

        thread(name = "Domrei-OpenJDK-Main", isDaemon = false) {
            val rawSpec = runCatching {
                DomreiJvmService.deserializeLaunchSpec(JSONObject(currentSpecFile.readText()))
            }.getOrElse { err ->
                appendDirectLog(currentLogFile, "[GameActivity] Failed to parse launch spec: ${err.message}")
                return@thread
            }

            // Update window dimensions to match actual SurfaceView resolution
            val updatedJvmArgs = rawSpec.jvmArguments.map { arg ->
                when {
                    arg.startsWith("-Dglfwstub.windowWidth=") -> "-Dglfwstub.windowWidth=$surfaceWidth"
                    arg.startsWith("-Dglfwstub.windowHeight=") -> "-Dglfwstub.windowHeight=$surfaceHeight"
                    arg.startsWith("-Ddomrei.window.width=") -> "-Ddomrei.window.width=$surfaceWidth"
                    arg.startsWith("-Ddomrei.window.height=") -> "-Ddomrei.window.height=$surfaceHeight"
                    else -> arg
                }
            }
            val updatedEnv = rawSpec.environment.toMutableMap().apply {
                put("DOMREI_WINDOW_WIDTH", surfaceWidth.toString())
                put("DOMREI_WINDOW_HEIGHT", surfaceHeight.toString())
            }
            val spec = rawSpec.copy(
                jvmArguments = updatedJvmArgs,
                environment = updatedEnv
            )

            mainHandler.post {
                statusBadgeView.text = "${spec.instanceName} • ${spec.renderer.type.displayName}"
            }

            val exitFile = File(spec.gameDir, DomreiJvmService.EXIT_FILE_NAME)
            exitFile.delete()

            appendDirectLog(
                currentLogFile,
                "[GameActivity] Surface ready (${surfaceWidth}x${surfaceHeight}, ANativeWindow=0x${java.lang.Long.toHexString(nativeWindowPtr)}), starting OpenJDK ${spec.runtime.version.major} (PID ${Process.myPid()})"
            )

            val exitCode = runCatching {
                DomreiJliRunner.launchInCurrentProcess(
                    spec = spec,
                    logFile = currentLogFile,
                    nativeWindowPtr = nativeWindowPtr
                ) { diag ->
                    appendDirectLog(currentLogFile, diag)
                }
            }.getOrElse { error ->
                appendDirectLog(currentLogFile, "[GameActivity] Fatal JLI_Launch error: ${error.stackTraceToString()}")
                -1
            }

            appendDirectLog(currentLogFile, "[GameActivity] JLI_Launch returned exit code $exitCode")
            runCatching { exitFile.writeText(exitCode.toString()) }

            mainHandler.post {
                statusBadgeView.text = "${spec.instanceName} (Exited: $exitCode)"
                setLogsOverlayVisible(true)
            }
        }
    }

    private fun startLogTailer() {
        val currentLogFile = logFile ?: return
        logPollingActive = true
        thread(name = "Domrei-GameActivity-LogTailer", isDaemon = true) {
            var pointer = 0L
            val linesBuffer = ArrayDeque<String>(180)
            while (logPollingActive) {
                if (currentLogFile.isFile && currentLogFile.length() > pointer) {
                    var changed = false
                    runCatching {
                        RandomAccessFile(currentLogFile, "r").use { raf ->
                            raf.seek(pointer)
                            var line = raf.readLine()
                            while (line != null) {
                                if (linesBuffer.size >= 160) {
                                    linesBuffer.removeFirst()
                                }
                                linesBuffer.addLast(line)
                                changed = true
                                if (!autoHiddenLogsOnGlCreated && (line.contains("[DomreiGLFW] Attached EGL WindowSurface") || line.contains("Sound engine started"))) {
                                    autoHiddenLogsOnGlCreated = true
                                    mainHandler.post {
                                        setLogsOverlayVisible(false)
                                    }
                                }
                                line = raf.readLine()
                            }
                            pointer = raf.filePointer
                        }
                    }
                    if (changed) {
                        val snapshot = linesBuffer.joinToString("\n")
                        mainHandler.post {
                            logTextView.text = snapshot
                            logScrollView.post {
                                logScrollView.fullScroll(View.FOCUS_DOWN)
                            }
                        }
                    }
                }
                Thread.sleep(150L)
            }
        }
    }

    private fun setLogsOverlayVisible(visible: Boolean) {
        logsVisible = visible
        logOverlayContainer.visibility = if (visible) View.VISIBLE else View.GONE
        toggleLogsButton.text = if (visible) "Hide Logs" else "Show Logs"
    }

    private fun finishAndKillJvm() {
        logPollingActive = false
        specFile?.parentFile?.let { gameDir ->
            runCatching {
                File(gameDir, DomreiJvmService.EXIT_FILE_NAME).writeText("0")
            }
        }
        finish()
        Process.killProcess(Process.myPid())
    }

    private fun enableImmersiveFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
        }
    }

    private fun appendDirectLog(logFile: File, line: String) {
        runCatching {
            logFile.parentFile?.mkdirs()
            FileOutputStream(logFile, true).use { fos ->
                fos.write((line + "\n").toByteArray(Charsets.UTF_8))
                fos.flush()
            }
        }
    }

    private fun initInputBridgeBuffer() {
        runCatching {
            val tmpDir = File(filesDir, "tmp").apply { mkdirs() }
            val mmapFile = File(tmpDir, "domrei_input.mmap")
            RandomAccessFile(mmapFile, "rw").use { raf ->
                raf.setLength(1024L)
                val buf = raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, 1024L)
                buf.order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until 256) {
                    buf.putInt(i * 4, 0)
                }
                inputMmap = buf
            }
        }
    }

    private fun buildTouchControlsOverlay(): FrameLayout {
        val overlay = FrameLayout(this)

        // Bottom-left D-Pad (W=87, A=65, S=83, D=68, Sneak=340 Left Shift)
        val dpadGrid = FrameLayout(this)
        val btnSize = dp(48)
        val gap = dp(52)

        dpadGrid.addView(
            createHoldKeyButton("W", 87),
            FrameLayout.LayoutParams(btnSize, btnSize).apply {
                leftMargin = gap
                topMargin = 0
            }
        )
        dpadGrid.addView(
            createHoldKeyButton("A", 65),
            FrameLayout.LayoutParams(btnSize, btnSize).apply {
                leftMargin = 0
                topMargin = gap
            }
        )
        val sneakBtn = TextView(this).apply {
            text = "SNK"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            background = createPadButtonDrawable(active = false)
            setOnClickListener {
                sneakToggled = !sneakToggled
                background = createPadButtonDrawable(active = sneakToggled)
                // GLFW_KEY_LEFT_SHIFT = 340
                writeInputEvent(type = 3, p1 = 340, p2 = 0, p3 = if (sneakToggled) 1 else 0, f1 = 0f, f2 = 0f)
            }
        }
        dpadGrid.addView(
            sneakBtn,
            FrameLayout.LayoutParams(btnSize, btnSize).apply {
                leftMargin = gap
                topMargin = gap
            }
        )
        dpadGrid.addView(
            createHoldKeyButton("D", 68),
            FrameLayout.LayoutParams(btnSize, btnSize).apply {
                leftMargin = gap * 2
                topMargin = gap
            }
        )
        dpadGrid.addView(
            createHoldKeyButton("S", 83),
            FrameLayout.LayoutParams(btnSize, btnSize).apply {
                leftMargin = gap
                topMargin = gap * 2
            }
        )

        overlay.addView(
            dpadGrid,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START
            ).apply {
                setMargins(dp(20), dp(16), dp(16), dp(20))
            }
        )

        // Bottom-right Action Cluster (PRI=Left Click, SEC=Right Click, JUMP=32 Space, INV=69 E)
        val rightCluster = FrameLayout(this)
        rightCluster.addView(
            createHoldMouseButton("PRI", button = 0),
            FrameLayout.LayoutParams(dp(56), btnSize).apply {
                leftMargin = 0
                topMargin = gap
            }
        )
        rightCluster.addView(
            createHoldMouseButton("SEC", button = 1),
            FrameLayout.LayoutParams(dp(56), btnSize).apply {
                leftMargin = dp(62)
                topMargin = 0
            }
        )
        rightCluster.addView(
            createHoldKeyButton("JUMP", 32),
            FrameLayout.LayoutParams(dp(56), btnSize).apply {
                leftMargin = dp(62)
                topMargin = gap * 2
            }
        )
        rightCluster.addView(
            createHoldKeyButton("INV", 69),
            FrameLayout.LayoutParams(dp(56), btnSize).apply {
                leftMargin = dp(124)
                topMargin = gap
            }
        )

        overlay.addView(
            rightCluster,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply {
                setMargins(dp(16), dp(16), dp(22), dp(20))
            }
        )

        return overlay
    }

    private fun createHoldKeyButton(label: String, glfwKey: Int): TextView {
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            background = createPadButtonDrawable(active = false)
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        background = createPadButtonDrawable(active = true)
                        writeInputEvent(type = 3, p1 = glfwKey, p2 = 0, p3 = 1, f1 = 0f, f2 = 0f)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        background = createPadButtonDrawable(active = false)
                        writeInputEvent(type = 3, p1 = glfwKey, p2 = 0, p3 = 0, f1 = 0f, f2 = 0f)
                    }
                }
                true
            }
        }
    }

    private fun createHoldMouseButton(label: String, button: Int): TextView {
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            background = createPadButtonDrawable(active = false)
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        background = createPadButtonDrawable(active = true)
                        writeInputEvent(type = 2, p1 = button, p2 = 1, p3 = 0, f1 = virtualLookX, f2 = virtualLookY)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        background = createPadButtonDrawable(active = false)
                        writeInputEvent(type = 2, p1 = button, p2 = 0, p3 = 0, f1 = virtualLookX, f2 = virtualLookY)
                    }
                }
                true
            }
        }
    }

    private fun createPadButtonDrawable(active: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            setColor(if (active) Color.argb(190, 56, 239, 125) else Color.argb(135, 18, 24, 32))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), if (active) Color.WHITE else Color.argb(110, 255, 255, 255))
        }
    }

    private fun tapGlfwKey(glfwKey: Int) {
        writeInputEvent(type = 3, p1 = glfwKey, p2 = 0, p3 = 1, f1 = 0f, f2 = 0f)
        writeInputEvent(type = 3, p1 = glfwKey, p2 = 0, p3 = 0, f1 = 0f, f2 = 0f)
    }

    private fun isCursorGrabbed(): Boolean {
        val buf = inputMmap ?: return false
        return runCatching { buf.getInt(8) == 1 }.getOrDefault(false)
    }

    private fun handleSurfaceTouchEvent(event: MotionEvent) {
        val x = event.x
        val y = event.y
        if (isCursorGrabbed()) {
            // In-game 3D camera look mode: relative delta rotation without cursor jumping
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = x
                    lastTouchY = y
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (x - lastTouchX) * 1.35f
                    val dy = (y - lastTouchY) * 1.35f
                    lastTouchX = x
                    lastTouchY = y
                    virtualLookX += dx
                    virtualLookY += dy
                    writeInputEvent(type = 1, p1 = 0, p2 = 0, p3 = 0, f1 = virtualLookX, f2 = virtualLookY)
                }
            }
        } else {
            // GUI / Menu cursor mode: 1:1 absolute touch-to-mouse click
            virtualLookX = x
            virtualLookY = y
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    writeInputEvent(type = 1, p1 = 0, p2 = 0, p3 = 0, f1 = x, f2 = y)
                    writeInputEvent(type = 2, p1 = 0, p2 = 1, p3 = 0, f1 = x, f2 = y)
                }
                MotionEvent.ACTION_MOVE -> {
                    writeInputEvent(type = 1, p1 = 0, p2 = 0, p3 = 0, f1 = x, f2 = y)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    writeInputEvent(type = 2, p1 = 0, p2 = 0, p3 = 0, f1 = x, f2 = y)
                }
            }
        }
    }

    private fun writeInputEvent(type: Int, p1: Int, p2: Int, p3: Int, f1: Float, f2: Float) {
        val buf = inputMmap ?: return
        synchronized(inputLock) {
            runCatching {
                val writeIdx = buf.getInt(0)
                val slotOffset = 64 + (writeIdx and 31) * 24
                buf.putInt(slotOffset, type)
                buf.putInt(slotOffset + 4, p1)
                buf.putInt(slotOffset + 8, p2)
                buf.putInt(slotOffset + 12, p3)
                buf.putFloat(slotOffset + 16, f1)
                buf.putFloat(slotOffset + 20, f2)
                buf.putInt(0, writeIdx + 1)
            }
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
