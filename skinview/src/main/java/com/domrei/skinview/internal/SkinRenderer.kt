package com.domrei.skinview.internal

import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import com.domrei.skinview.PartPose
import com.domrei.skinview.PlayerPose
import com.domrei.skinview.SkinModel
import com.domrei.skinview.SkinViewerConfig
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max
import kotlin.math.tan

/** OpenGL ES 2.0 renderer for a Minecraft player model. Thread-safe setters, renders on the GL thread. */
internal class SkinRenderer : GLSurfaceView.Renderer {

    @Volatile var config: SkinViewerConfig = SkinViewerConfig()

    // Camera, written by the UI thread.
    @Volatile var userYaw = 0f
    @Volatile var userPitch = 0f
    @Volatile var zoom = 1f
    @Volatile private var resetAutoRotate = false
    @Volatile private var resetAnimationTime = false

    /** Current total yaw including auto-rotation (read-only for callers). */
    @Volatile var currentYaw = 0f
        private set

    // Textures, swapped by the UI thread, uploaded on the GL thread.
    @Volatile private var skinBitmap: Bitmap? = null
    @Volatile private var skinDetectedSlim = false
    @Volatile private var skinDirty = false
    @Volatile private var capeBitmap: Bitmap? = null
    @Volatile private var capeDirty = false

    @Volatile private var cachedNametagText: String? = null
    @Volatile private var nametagBitmap: Bitmap? = null
    @Volatile private var nametagDirty = false
    private var nametagTex = 0
    private var nametagAspect = 1f
    private val nametagMesh = BillboardMesh()

    private var program = 0
    private var aPos = 0
    private var aUV = 0
    private var aNormal = 0
    private var uMVP = 0
    private var uModel = 0
    private var uTex = 0
    private var uOpaque = 0
    private var uLightDir = 0
    private var uAmbient = 0
    private var uDiffuse = 0

    private var skinTex = 0
    private var capeTex = 0

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProj = FloatArray(16)
    private val root = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private var aspect = 1f

    private val pose = PlayerPose()
    private var animTime = 0f
    private var autoYaw = 0f
    private var lastFrameNanos = 0L

    private val meshes = Meshes()

    fun setSkin(bitmap: Bitmap?, detectedSlim: Boolean) {
        skinDetectedSlim = detectedSlim
        skinBitmap = bitmap
        skinDirty = true
    }

    fun setCape(bitmap: Bitmap?) {
        capeBitmap = bitmap
        capeDirty = true
    }

    fun resetAutoRotation() { resetAutoRotate = true }
    fun restartAnimation() { resetAnimationTime = true }

    override fun onSurfaceCreated(gl: GL10?, eglConfig: EGLConfig?) {
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aUV = GLES20.glGetAttribLocation(program, "aUV")
        aNormal = GLES20.glGetAttribLocation(program, "aNormal")
        uMVP = GLES20.glGetUniformLocation(program, "uMVP")
        uModel = GLES20.glGetUniformLocation(program, "uModel")
        uTex = GLES20.glGetUniformLocation(program, "uTex")
        uOpaque = GLES20.glGetUniformLocation(program, "uOpaque")
        uLightDir = GLES20.glGetUniformLocation(program, "uLightDir")
        uAmbient = GLES20.glGetUniformLocation(program, "uAmbient")
        uDiffuse = GLES20.glGetUniformLocation(program, "uDiffuse")

        // GL context is new: old texture names are invalid.
        skinTex = 0
        capeTex = 0
        nametagTex = 0
        skinDirty = true
        capeDirty = true
        nametagDirty = true
        cachedNametagText = null
        lastFrameNanos = 0L

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glFrontFace(GLES20.GL_CCW)
        GLES20.glCullFace(GLES20.GL_BACK)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = if (height == 0) 1f else width.toFloat() / height
    }

    override fun onDrawFrame(gl: GL10?) {
        val cfg = config
        uploadTexturesIfNeeded()

        // --- timing ---
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0f else ((now - lastFrameNanos) / 1e9f).coerceAtMost(0.1f)
        lastFrameNanos = now
        if (resetAnimationTime) { animTime = 0f; resetAnimationTime = false }
        if (resetAutoRotate) { autoYaw = 0f; resetAutoRotate = false }
        if (cfg.animation != null && !cfg.animationPaused) animTime += dt * cfg.animationSpeed
        if (cfg.autoRotate) autoYaw = (autoYaw + dt * cfg.autoRotateSpeed) % 360f

        // --- clear ---
        val bg = cfg.backgroundColor
        GLES20.glClearColor(Color.red(bg) / 255f, Color.green(bg) / 255f, Color.blue(bg) / 255f, Color.alpha(bg) / 255f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (skinTex == 0) return

        // --- pose ---
        pose.reset()
        try {
            cfg.animation?.animate(pose, animTime)
        } catch (t: Throwable) {
            Log.e(TAG, "Animation threw", t)
        }

        // --- camera ---
        val halfFov = Math.toRadians(cfg.fieldOfView / 2.0).toFloat()
        val t = tan(halfFov)
        val fitDistance = max(MODEL_HALF_EXTENT / t, MODEL_HALF_EXTENT / (t * aspect)) + MODEL_DEPTH_MARGIN
        val distance = fitDistance / zoom.coerceAtLeast(0.01f)
        Matrix.perspectiveM(proj, 0, cfg.fieldOfView, aspect, 1f, 1000f)
        Matrix.setLookAtM(view, 0, 0f, 0f, distance, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(viewProj, 0, proj, 0, view, 0)

        val yaw = userYaw + autoYaw
        currentYaw = yaw
        Matrix.setIdentityM(root, 0)
        Matrix.rotateM(root, 0, userPitch, 1f, 0f, 0f)
        Matrix.rotateM(root, 0, yaw, 0f, 1f, 0f)
        applyPose(root, pose.root)
        Matrix.translateM(root, 0, 0f, -MODEL_CENTER_Y, 0f)

        // --- shader setup ---
        GLES20.glUseProgram(program)
        val l = cfg.lighting
        GLES20.glUniform3f(uLightDir, l.directionX, l.directionY, l.directionZ)
        GLES20.glUniform1f(uAmbient, l.ambient)
        GLES20.glUniform1f(uDiffuse, l.diffuse)
        GLES20.glUniform1i(uTex, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aUV)
        GLES20.glEnableVertexAttribArray(aNormal)

        val slim = when (cfg.model) {
            SkinModel.AUTO -> skinDetectedSlim
            SkinModel.CLASSIC -> false
            SkinModel.SLIM -> true
        }
        val armOffset = if (slim) 0.5f else 1f
        val v = cfg.parts

        // --- pass 1: inner layer (opaque) ---
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, skinTex)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glUniform1f(uOpaque, 1f)
        drawParts(inner = true, slim = slim, armOffset = armOffset, cfgParts = v)

        // --- pass 2: cape ---
        if (v.cape && capeTex != 0 && pose.cape.visible) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, capeTex)
            GLES20.glUniform1f(uOpaque, 0f)
            GLES20.glDisable(GLES20.GL_CULL_FACE)
            partMatrix(pose.cape, 0f, 24f, -2f, 0f, -8f, 0.5f, extraX = PlayerPose.DEFAULT_CAPE_ANGLE, flip = true)
            draw(meshes.cape)
        }

        // --- pass 3: outer layer (alpha blended, double sided) ---
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, skinTex)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA) // bitmaps are premultiplied
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glUniform1f(uOpaque, 0f)
        drawParts(inner = false, slim = slim, armOffset = armOffset, cfgParts = v)

        // --- pass 4: nametag billboard ---
        if (nametagTex != 0 && !cfg.nameTag.isNullOrBlank()) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, nametagTex)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glDisable(GLES20.GL_CULL_FACE)
            GLES20.glUniform1f(uOpaque, 0f)
            GLES20.glUniform1f(uAmbient, 1f)
            GLES20.glUniform1f(uDiffuse, 0f)

            val baseTagHeight = 3.2f
            val tagHeight = baseTagHeight * cfg.nameTagScale.coerceIn(0.5f, 5.0f)
            val tagWidth = tagHeight * nametagAspect

            // Head pivot is at y=24, head box extends +8 to y=32. Center nametag with neat margin above head:
            val tagCenterY = 32.5f + (tagHeight / 2f) + 0.8f
            val headTop = floatArrayOf(pose.head.x, tagCenterY + pose.head.y, pose.head.z, 1f)
            val worldPos = FloatArray(4)
            Matrix.multiplyMV(worldPos, 0, root, 0, headTop, 0)

            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, worldPos[0], worldPos[1], worldPos[2])
            Matrix.scaleM(model, 0, tagWidth, tagHeight, 1f)
            draw(nametagMesh)
        }

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aUV)
        GLES20.glDisableVertexAttribArray(aNormal)
    }

    private fun drawParts(inner: Boolean, slim: Boolean, armOffset: Float, cfgParts: com.domrei.skinview.PartVisibility) {
        fun shown(lv: com.domrei.skinview.LayerVisibility) = if (inner) lv.inner else lv.outer
        val set = if (inner) meshes.inner else meshes.outer
        val rightArm = if (slim) set.rightArmSlim else set.rightArm
        val leftArm = if (slim) set.leftArmSlim else set.leftArm

        if (shown(cfgParts.head) && pose.head.visible) {
            partMatrix(pose.head, 0f, 24f, 0f, 0f, 4f, 0f); draw(set.head)
        }
        if (shown(cfgParts.body) && pose.body.visible) {
            partMatrix(pose.body, 0f, 24f, 0f, 0f, -6f, 0f); draw(set.body)
        }
        if (shown(cfgParts.rightArm) && pose.rightArm.visible) {
            partMatrix(pose.rightArm, -5f, 22f, 0f, -armOffset, -4f, 0f); draw(rightArm)
        }
        if (shown(cfgParts.leftArm) && pose.leftArm.visible) {
            partMatrix(pose.leftArm, 5f, 22f, 0f, armOffset, -4f, 0f); draw(leftArm)
        }
        if (shown(cfgParts.rightLeg) && pose.rightLeg.visible) {
            partMatrix(pose.rightLeg, -1.9f, 12f, 0f, 0f, -6f, 0f); draw(set.rightLeg)
        }
        if (shown(cfgParts.leftLeg) && pose.leftLeg.visible) {
            partMatrix(pose.leftLeg, 1.9f, 12f, 0f, 0f, -6f, 0f); draw(set.leftLeg)
        }
    }

    /** model = root * T(pivot + offset) * Rz * Ry * Rx * [Ry(180)] * T(boxCenter) */
    private fun partMatrix(
        p: PartPose,
        px: Float, py: Float, pz: Float,
        ox: Float, oy: Float, oz: Float,
        extraX: Float = 0f,
        flip: Boolean = false,
    ) {
        System.arraycopy(root, 0, model, 0, 16)
        Matrix.translateM(model, 0, px + p.x, py + p.y, pz + p.z)
        rotate(model, p.rotZ, 0f, 0f, 1f)
        rotate(model, p.rotY, 0f, 1f, 0f)
        rotate(model, p.rotX + extraX, 1f, 0f, 0f)
        if (flip) Matrix.rotateM(model, 0, 180f, 0f, 1f, 0f)
        Matrix.translateM(model, 0, ox, oy, oz)
    }

    private fun applyPose(m: FloatArray, p: PartPose) {
        Matrix.translateM(m, 0, p.x, p.y, p.z)
        rotate(m, p.rotZ, 0f, 0f, 1f)
        rotate(m, p.rotY, 0f, 1f, 0f)
        rotate(m, p.rotX, 1f, 0f, 0f)
    }

    private fun rotate(m: FloatArray, radians: Float, x: Float, y: Float, z: Float) {
        if (radians != 0f) Matrix.rotateM(m, 0, Math.toDegrees(radians.toDouble()).toFloat(), x, y, z)
    }

    private fun draw(mesh: BoxMesh) {
        Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0)
        val b = mesh.buffer
        b.position(0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, BoxMesh.STRIDE_BYTES, b)
        b.position(3)
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, BoxMesh.STRIDE_BYTES, b)
        b.position(5)
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, BoxMesh.STRIDE_BYTES, b)
        b.position(0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount)
    }

    private fun draw(mesh: BillboardMesh) {
        Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0)
        val b = mesh.buffer
        b.position(0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, BoxMesh.STRIDE_BYTES, b)
        b.position(3)
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, BoxMesh.STRIDE_BYTES, b)
        b.position(5)
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, BoxMesh.STRIDE_BYTES, b)
        b.position(0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount)
    }

    private fun uploadTexturesIfNeeded() {
        if (skinDirty) {
            skinDirty = false
            skinTex = upload(skinTex, skinBitmap)
        }
        if (capeDirty) {
            capeDirty = false
            capeTex = upload(capeTex, capeBitmap)
        }
        val tagText = config.nameTag
        if (tagText != cachedNametagText) {
            cachedNametagText = tagText
            nametagBitmap = if (tagText.isNullOrBlank()) null else NametagGenerator.create(tagText)
            nametagDirty = true
        }
        if (nametagDirty) {
            nametagDirty = false
            val bmp = nametagBitmap
            nametagAspect = if (bmp != null && bmp.height > 0) bmp.width.toFloat() / bmp.height else 1f
            nametagTex = upload(nametagTex, bmp, filter = GLES20.GL_LINEAR)
        }
    }

    private fun upload(existing: Int, bitmap: Bitmap?, filter: Int = GLES20.GL_NEAREST): Int {
        if (existing != 0) GLES20.glDeleteTextures(1, intArrayOf(existing), 0)
        if (bitmap == null || bitmap.isRecycled) return 0
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return ids[0]
    }

    /** All meshes, built once. UVs are in 64-pixel base units so HD skins work too. */
    private class Meshes {
        class PartSet(
            val head: BoxMesh,
            val body: BoxMesh,
            val rightArm: BoxMesh,
            val leftArm: BoxMesh,
            val rightArmSlim: BoxMesh,
            val leftArmSlim: BoxMesh,
            val rightLeg: BoxMesh,
            val leftLeg: BoxMesh,
        )

        private fun skinBox(w: Float, h: Float, d: Float, u: Float, v: Float, inflate: Float) =
            BoxMesh(w, h, d, u, v, 64f, 64f, inflate)

        val inner = PartSet(
            head = skinBox(8f, 8f, 8f, 0f, 0f, 0f),
            body = skinBox(8f, 12f, 4f, 16f, 16f, 0f),
            rightArm = skinBox(4f, 12f, 4f, 40f, 16f, 0f),
            leftArm = skinBox(4f, 12f, 4f, 32f, 48f, 0f),
            rightArmSlim = skinBox(3f, 12f, 4f, 40f, 16f, 0f),
            leftArmSlim = skinBox(3f, 12f, 4f, 32f, 48f, 0f),
            rightLeg = skinBox(4f, 12f, 4f, 0f, 16f, 0f),
            leftLeg = skinBox(4f, 12f, 4f, 16f, 48f, 0f),
        )

        val outer = PartSet(
            head = skinBox(8f, 8f, 8f, 32f, 0f, 0.5f),
            body = skinBox(8f, 12f, 4f, 16f, 32f, 0.25f),
            rightArm = skinBox(4f, 12f, 4f, 40f, 32f, 0.25f),
            leftArm = skinBox(4f, 12f, 4f, 48f, 48f, 0.25f),
            rightArmSlim = skinBox(3f, 12f, 4f, 40f, 32f, 0.25f),
            leftArmSlim = skinBox(3f, 12f, 4f, 48f, 48f, 0.25f),
            rightLeg = skinBox(4f, 12f, 4f, 0f, 32f, 0.25f),
            leftLeg = skinBox(4f, 12f, 4f, 0f, 48f, 0.25f),
        )

        val cape = BoxMesh(10f, 16f, 1f, 0f, 0f, 64f, 32f)
    }

    private fun buildProgram(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                val log = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                error("Shader compile failed: $log")
            }
            return shader
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        val status = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) error("Program link failed: ${GLES20.glGetProgramInfoLog(p)}")
        return p
    }

    private companion object {
        const val TAG = "SkinRenderer"

        /** Model spans y = 0..32 (+ hat overlay). Center vertically. */
        const val MODEL_CENTER_Y = 16f
        /** Half of the model's size used to fit the camera, with room for raised arms / bobbing. */
        const val MODEL_HALF_EXTENT = 19f
        const val MODEL_DEPTH_MARGIN = 6f

        const val VERTEX_SHADER = """
            uniform mat4 uMVP;
            uniform mat4 uModel;
            uniform vec3 uLightDir;
            uniform float uAmbient;
            uniform float uDiffuse;
            attribute vec3 aPos;
            attribute vec2 aUV;
            attribute vec3 aNormal;
            varying vec2 vUV;
            varying float vLight;
            void main() {
                vec3 n = normalize((uModel * vec4(aNormal, 0.0)).xyz);
                vLight = min(uAmbient + uDiffuse * max(dot(n, normalize(uLightDir)), 0.0), 1.0);
                vUV = aUV;
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTex;
            uniform float uOpaque;
            varying vec2 vUV;
            varying float vLight;
            void main() {
                vec4 c = texture2D(uTex, vUV);
                if (uOpaque > 0.5) {
                    c.a = 1.0;
                } else if (c.a < 0.1) {
                    discard;
                }
                gl_FragColor = vec4(c.rgb * vLight, c.a);
            }
        """
    }
}
