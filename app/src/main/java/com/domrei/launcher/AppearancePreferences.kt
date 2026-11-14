package com.domrei.launcher

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.domrei.launcher.ui.theme.LauncherColorTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Persists user Appearance customization (custom Image/GIF/Video background and Accent Color)
 * in private app storage and SharedPreferences so picked media never loses URI permissions
 * after app restart.
 */
class AppearancePreferences(context: Context) {
    private val appContext = context.applicationContext
    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val backgroundsDir: File by lazy {
        File(appContext.filesDir, BACKGROUNDS_DIR_NAME)
    }

    fun loadAccentColor(): Color {
        val defaultArgb = LauncherColorTokens.DefaultAccent.toArgb()
        val storedArgb = runCatching { prefs.getInt(KEY_ACCENT_ARGB, defaultArgb) }
            .getOrDefault(defaultArgb)
        return Color(storedArgb)
    }

    fun saveAccentColor(color: Color) {
        prefs.edit()
            .putInt(KEY_ACCENT_ARGB, color.toArgb())
            .apply()
    }

    fun loadBackgroundConfig(): BackgroundConfig {
        val type = runCatching { prefs.getString(KEY_BG_TYPE, TYPE_SUNSET) }
            .getOrDefault(TYPE_SUNSET)
        val path = runCatching { prefs.getString(KEY_BG_PATH, null) }.getOrNull()

        val source = when (type) {
            TYPE_IMAGE -> path?.let(::existingFileUri)?.let { BackgroundSource.Image(uri = it) }
            TYPE_GIF -> path?.let(::existingFileUri)?.let { BackgroundSource.Gif(uri = it) }
            TYPE_VIDEO -> path?.let(::existingFileUri)?.let { BackgroundSource.Video(uri = it) }
            else -> null
        } ?: BackgroundSource.MinecraftSunset()

        return BackgroundConfig(source = source)
    }

    fun saveBackgroundConfig(config: BackgroundConfig) {
        val (type, filePath) = when (val src = config.source) {
            is BackgroundSource.Image -> TYPE_IMAGE to src.uri?.path
            is BackgroundSource.Gif -> TYPE_GIF to src.uri?.path
            is BackgroundSource.Video -> TYPE_VIDEO to src.uri?.path
            is BackgroundSource.MinecraftSunset,
            is BackgroundSource.SolidColor -> TYPE_SUNSET to null
        }
        prefs.edit()
            .putString(KEY_BG_TYPE, type)
            .apply {
                if (filePath != null) {
                    putString(KEY_BG_PATH, filePath)
                } else {
                    remove(KEY_BG_PATH)
                }
            }
            .remove(LEGACY_KEY_BG_DIM_ALPHA)
            .apply()
    }

    fun clearCustomBackgroundFiles() {
        runCatching {
            if (backgroundsDir.exists()) {
                backgroundsDir.listFiles()?.forEach { it.delete() }
            }
        }
    }

    /**
     * Copies an Image, GIF, or Video URI picked via Storage Access Framework into private app
     * storage on [Dispatchers.IO] and detects its media kind from MIME type, file name, and
     * container header bytes.
     */
    suspend fun importCustomBackground(
        sourceUri: Uri,
    ): Result<BackgroundSource> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = appContext.contentResolver
            val mimeType = resolver.getType(sourceUri)?.lowercase().orEmpty()
            val displayName = queryDisplayName(resolver, sourceUri)?.lowercase().orEmpty()
            val fileExt = displayName.substringAfterLast('.', "").takeIf {
                it.isNotEmpty() && it.length <= 6 && it.all(Char::isLetterOrDigit)
            }.orEmpty()

            val header = ByteArray(16)
            val input = resolver.openInputStream(sourceUri)
                ?: error("Unable to open selected file")

            val (savedFile, mediaKind) = input.use { stream ->
                val count = stream.read(header)
                val detectedKind = classifyMedia(
                    mimeType = mimeType,
                    fileExt = fileExt,
                    header = header,
                    headerLen = count,
                ) ?: throw IllegalArgumentException("Unsupported background media format")

                val ext = resolveExtension(
                    mimeType = mimeType,
                    fileExt = fileExt,
                    kind = detectedKind,
                )
                backgroundsDir.mkdirs()
                val outFile = File(backgroundsDir, "custom_bg_${System.currentTimeMillis()}.$ext")
                outFile.outputStream().use { out ->
                    if (count > 0) {
                        out.write(header, 0, count)
                    }
                    stream.copyTo(out)
                }
                backgroundsDir.listFiles()?.forEach { existing ->
                    if (existing.absolutePath != outFile.absolutePath) {
                        existing.delete()
                    }
                }
                outFile to detectedKind
            }

            val localUri = Uri.fromFile(savedFile)
            when (mediaKind) {
                DetectedMediaKind.GIF -> BackgroundSource.Gif(uri = localUri)
                DetectedMediaKind.VIDEO -> BackgroundSource.Video(uri = localUri)
                DetectedMediaKind.IMAGE -> BackgroundSource.Image(uri = localUri)
            }
        }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return c.getString(idx)
                }
            }
        }
        return uri.lastPathSegment
    }

    private fun classifyMedia(
        mimeType: String,
        fileExt: String,
        header: ByteArray,
        headerLen: Int,
    ): DetectedMediaKind? {
        val isGifHeader = isAsciiAt(header, headerLen, 0, "GIF87a") ||
            isAsciiAt(header, headerLen, 0, "GIF89a")
        if (mimeType == "image/gif" || isGifHeader || fileExt == "gif") {
            return DetectedMediaKind.GIF
        }

        val isFtyp = isAsciiAt(header, headerLen, 4, "ftyp")
        val ftypBrand = if (isFtyp && headerLen >= 12) {
            String(header, 8, 4, Charsets.US_ASCII).lowercase()
        } else {
            ""
        }
        val isHeifOrAvif = isFtyp && ftypBrand in IMAGE_ISOBMFF_BRANDS
        val isVideoMagic = (isFtyp && !isHeifOrAvif) ||
            (headerLen >= 4 &&
                header[0] == 0x1A.toByte() &&
                header[1] == 0x45.toByte() &&
                header[2] == 0xDF.toByte() &&
                header[3] == 0xA3.toByte()) ||
            (isAsciiAt(header, headerLen, 0, "RIFF") && isAsciiAt(header, headerLen, 8, "AVI ")) ||
            isAsciiAt(header, headerLen, 0, "FLV")

        if (mimeType.startsWith("video/") || isVideoMagic || fileExt in VIDEO_EXTENSIONS) {
            return DetectedMediaKind.VIDEO
        }

        val isImageMagic = isHeifOrAvif ||
            (headerLen >= 4 &&
                header[0] == 0x89.toByte() &&
                header[1] == 0x50.toByte() &&
                header[2] == 0x4E.toByte() &&
                header[3] == 0x47.toByte()) ||
            (headerLen >= 3 &&
                header[0] == 0xFF.toByte() &&
                header[1] == 0xD8.toByte() &&
                header[2] == 0xFF.toByte()) ||
            (isAsciiAt(header, headerLen, 0, "RIFF") && isAsciiAt(header, headerLen, 8, "WEBP")) ||
            isAsciiAt(header, headerLen, 0, "BM")

        if (mimeType.startsWith("image/") || isImageMagic || fileExt in IMAGE_EXTENSIONS) {
            return DetectedMediaKind.IMAGE
        }

        return null
    }

    private fun isAsciiAt(bytes: ByteArray, length: Int, offset: Int, text: String): Boolean {
        if (length < offset + text.length) return false
        for (i in text.indices) {
            if (bytes[offset + i] != text[i].code.toByte()) return false
        }
        return true
    }

    private fun resolveExtension(
        mimeType: String,
        fileExt: String,
        kind: DetectedMediaKind,
    ): String {
        if (kind == DetectedMediaKind.GIF) return "gif"
        val fromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        if (!fromMime.isNullOrBlank()) return fromMime
        if (fileExt.isNotEmpty()) return fileExt
        return if (kind == DetectedMediaKind.VIDEO) "mp4" else "png"
    }

    private fun existingFileUri(path: String): Uri? {
        val file = File(path)
        return if (file.isFile) Uri.fromFile(file) else null
    }

    private enum class DetectedMediaKind {
        IMAGE,
        GIF,
        VIDEO,
    }

    companion object {
        private const val PREFS_NAME = "domrei_appearance"
        private const val BACKGROUNDS_DIR_NAME = "backgrounds"
        private const val KEY_ACCENT_ARGB = "accent_color_argb"
        private const val KEY_BG_TYPE = "bg_type"
        private const val KEY_BG_PATH = "bg_path"
        private const val LEGACY_KEY_BG_DIM_ALPHA = "bg_dim_alpha"
        private const val TYPE_SUNSET = "sunset"
        private const val TYPE_IMAGE = "image"
        private const val TYPE_GIF = "gif"
        private const val TYPE_VIDEO = "video"

        private val IMAGE_ISOBMFF_BRANDS = setOf(
            "heic", "heix", "hevc", "hevx", "mif1", "msf1", "avif", "avis",
        )
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "m4v", "mov", "mkv", "webm", "3gp", "avi", "flv", "wmv", "ts", "mpg", "mpeg",
        )
        private val IMAGE_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "webp", "bmp", "heic", "heif", "avif",
        )
    }
}
