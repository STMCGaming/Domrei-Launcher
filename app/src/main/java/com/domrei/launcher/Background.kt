package com.domrei.launcher

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.sin

/**
 * Defines the background source for the launcher.
 * Supports animated 2D Minecraft sunset (default), custom static images, animated GIFs,
 * looping videos, or solid colors.
 */
@Immutable
sealed interface BackgroundSource {
    /** Default animated 2D Minecraft-style sunset scene. */
    data class MinecraftSunset(
        val animationSpeed: Float = 1f,
        val showStars: Boolean = true,
        val showClouds: Boolean = true,
    ) : BackgroundSource

    /** Static image background from a URI, drawable resource, or asset path. */
    data class Image(
        val uri: Uri? = null,
        @DrawableRes val resId: Int? = null,
        val assetPath: String? = null,
    ) : BackgroundSource

    /** Animated GIF background from a URI, raw/drawable resource, or asset path. */
    data class Gif(
        val uri: Uri? = null,
        @RawRes @DrawableRes val resId: Int? = null,
        val assetPath: String? = null,
    ) : BackgroundSource

    /** Looping muted video background from a URI, raw resource, or asset path. */
    data class Video(
        val uri: Uri? = null,
        @RawRes val rawResId: Int? = null,
        val assetPath: String? = null,
    ) : BackgroundSource

    /** Simple solid color background. */
    data class SolidColor(val color: Color) : BackgroundSource
}

/**
 * High-level configuration for [LauncherBackground].
 */
@Immutable
data class BackgroundConfig(
    val source: BackgroundSource = BackgroundSource.MinecraftSunset(),
    /** Optional dark overlay alpha (0f..1f) to improve foreground UI contrast over custom media. */
    val dimAlpha: Float = 0f,
)

/**
 * Shared frame-synchronized animation clock in seconds so that real-time blurred UI surfaces
 * (such as the navigation rail) stay 100% in sync with the live background canvas.
 */
@Composable
fun rememberBackgroundTimeSeconds(animationSpeed: Float = 1f): Float {
    var timeSeconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animationSpeed) {
        var lastNanos = -1L
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (lastNanos >= 0L) {
                    val dt = ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    timeSeconds += dt * animationSpeed
                }
                lastNanos = frameNanos
            }
        }
    }
    return timeSeconds
}

/**
 * Root background composable for the launcher.
 * Renders the configured [BackgroundSource] and falls back to [MinecraftSunsetBackground]
 * if a custom media file cannot be loaded.
 */
@Composable
fun LauncherBackground(
    modifier: Modifier = Modifier,
    config: BackgroundConfig = BackgroundConfig(),
    sharedTimeSeconds: Float? = null,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (val source = config.source) {
            is BackgroundSource.MinecraftSunset -> MinecraftSunsetBackground(
                modifier = Modifier.fillMaxSize(),
                source = source,
                sharedTimeSeconds = sharedTimeSeconds,
            )
            is BackgroundSource.Image -> CustomImageBackground(
                modifier = Modifier.fillMaxSize(),
                source = source,
            )
            is BackgroundSource.Gif -> CustomGifBackground(
                modifier = Modifier.fillMaxSize(),
                source = source,
            )
            is BackgroundSource.Video -> CustomVideoBackground(
                modifier = Modifier.fillMaxSize(),
                source = source,
            )
            is BackgroundSource.SolidColor -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(source.color),
            )
        }

        if (config.dimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = config.dimAlpha.coerceIn(0f, 1f))),
            )
        }
    }
}

// =========================================================================================
// Animated 2D Minecraft-Style Sunset Background (Smooth Clouds, No Fireflies)
// =========================================================================================

private data class StarSpec(val xFrac: Float, val yFrac: Float, val radiusBlocks: Float, val phase: Float)
private data class CloudSpec(val baseXFrac: Float, val yFrac: Float, val speed: Float, val scale: Float, val blocks: IntArray)

@Composable
fun MinecraftSunsetBackground(
    modifier: Modifier = Modifier,
    source: BackgroundSource.MinecraftSunset = BackgroundSource.MinecraftSunset(),
    sharedTimeSeconds: Float? = null,
) {
    val internalTime = if (sharedTimeSeconds == null) {
        rememberBackgroundTimeSeconds(source.animationSpeed)
    } else {
        0f
    }
    val timeSeconds = sharedTimeSeconds ?: internalTime

    val stars = remember {
        listOf(
            StarSpec(0.06f, 0.07f, 0.35f, 0.0f),
            StarSpec(0.14f, 0.16f, 0.30f, 1.2f),
            StarSpec(0.22f, 0.05f, 0.45f, 2.5f),
            StarSpec(0.31f, 0.21f, 0.32f, 0.7f),
            StarSpec(0.38f, 0.10f, 0.38f, 3.8f),
            StarSpec(0.46f, 0.06f, 0.42f, 1.9f),
            StarSpec(0.55f, 0.14f, 0.34f, 4.4f),
            StarSpec(0.64f, 0.07f, 0.36f, 2.1f),
            StarSpec(0.73f, 0.19f, 0.30f, 5.0f),
            StarSpec(0.82f, 0.06f, 0.44f, 0.4f),
            StarSpec(0.91f, 0.13f, 0.35f, 3.1f),
            StarSpec(0.10f, 0.29f, 0.28f, 4.9f),
            StarSpec(0.27f, 0.34f, 0.30f, 2.8f),
            StarSpec(0.68f, 0.31f, 0.32f, 1.1f),
            StarSpec(0.87f, 0.26f, 0.32f, 1.5f),
            StarSpec(0.95f, 0.36f, 0.28f, 3.6f),
        )
    }

    // Each cloud is defined by (baseXFrac, yFrac, speed, scale, puffs: IntArray of [colOffset, widthCols, topRow, bottomRow])
    val clouds = remember {
        listOf(
            CloudSpec(
                baseXFrac = 0.06f,
                yFrac = 0.16f,
                speed = 0.012f,
                scale = 1.0f,
                blocks = intArrayOf(
                    0, 3, 1, 3,
                    3, 5, 0, 3,
                    8, 4, 1, 3,
                ),
            ),
            CloudSpec(
                baseXFrac = 0.38f,
                yFrac = 0.11f,
                speed = 0.008f,
                scale = 0.85f,
                blocks = intArrayOf(
                    0, 3, 2, 3,
                    3, 4, 1, 3,
                    7, 5, 0, 3,
                    12, 3, 1, 3,
                ),
            ),
            CloudSpec(
                baseXFrac = 0.70f,
                yFrac = 0.20f,
                speed = 0.015f,
                scale = 1.08f,
                blocks = intArrayOf(
                    0, 4, 1, 3,
                    4, 6, 0, 3,
                    10, 3, 1, 3,
                ),
            ),
            CloudSpec(
                baseXFrac = 0.22f,
                yFrac = 0.27f,
                speed = 0.0065f,
                scale = 0.76f,
                blocks = intArrayOf(
                    0, 3, 1, 2,
                    3, 5, 0, 2,
                    8, 3, 1, 2,
                ),
            ),
        )
    }

    // 64-column seamless distant mountain heights (in pixel blocks above farBaseY)
    val farMountainHeights = remember {
        intArrayOf(
            7, 8, 9, 11, 13, 15, 17, 18, 19, 18, 16, 14, 12, 11, 12, 14,
            15, 14, 12, 10, 8, 7, 6, 5, 5, 6, 7, 9, 10, 11, 10, 8,
            7, 6, 7, 9, 11, 13, 16, 18, 20, 21, 20, 18, 16, 14, 12, 11,
            13, 15, 16, 15, 13, 11, 9, 8, 7, 6, 5, 6, 7, 8, 8, 7
        )
    }

    // 64-column seamless mid-ground forested hill heights
    val midHillHeights = remember {
        intArrayOf(
            5, 6, 7, 8, 9, 9, 8, 7, 6, 5, 5, 6, 7, 8, 7, 6,
            5, 4, 3, 3, 4, 5, 6, 6, 5, 4, 4, 5, 6, 7, 8, 8,
            7, 6, 5, 6, 7, 8, 9, 10, 10, 9, 8, 7, 6, 6, 7, 8,
            8, 7, 6, 5, 4, 4, 5, 6, 6, 5, 4, 4, 5, 5, 5, 5
        )
    }
    // 6 trees placed on the mid-ground ridge
    val midHillTreeCols = remember {
        intArrayOf(4, 12, 22, 39, 47, 55)
    }

    // 64-column seamless foreground blocky grass terrain heights
    val nearGroundHeights = remember {
        intArrayOf(
            4, 4, 5, 5, 5, 4, 4, 3, 3, 3, 2, 2, 3, 3, 4, 4,
            3, 3, 2, 2, 2, 3, 3, 4, 4, 3, 3, 4, 4, 5, 5, 4,
            4, 3, 3, 4, 4, 5, 5, 6, 6, 5, 5, 4, 4, 3, 3, 4,
            4, 5, 5, 4, 4, 3, 3, 2, 2, 3, 3, 4, 4, 3, 3, 4
        )
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Pixel block unit scaled nicely to screen height (e.g. 64 vertical pixel units)
        val block = (h / 64f).coerceAtLeast(2f)
        val horizonY = h * 0.68f

        // 1. Multi-stop Minecraft Night Sky Gradient
        drawRect(
            brush = Brush.verticalGradient(
                0.00f to Color(0xFF040711),
                0.22f to Color(0xFF081024),
                0.45f to Color(0xFF0F1D3A),
                0.68f to Color(0xFF172C54),
                0.86f to Color(0xFF213C6E),
                1.00f to Color(0xFF2C4E86),
                startY = 0f,
                endY = horizonY + block * 6f,
            ),
            size = Size(w, h),
        )

        // 2. Small Twinkling Square Stars in the Night Sky
        if (source.showStars) {
            for (star in stars) {
                val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(timeSeconds * 2.2f + star.phase))
                val sx = star.xFrac * w
                val sy = star.yFrac * h
                val sSize = block * star.radiusBlocks
                drawRect(
                    color = Color(0xFFE8F2FF).copy(alpha = twinkle * 0.90f),
                    topLeft = Offset(sx, sy),
                    size = Size(sSize, sSize),
                )
            }
        }

        // 3. Iconic Square Minecraft Moon
        val moonBob = sin(timeSeconds * 0.45f) * block * 0.7f
        val moonCenterX = w * 0.5f
        val moonCenterY = h * 0.28f + moonBob
        drawMinecraftMoon(moonCenterX, moonCenterY, block, timeSeconds)

        // 4. Cool Moonlit Horizon Mist Glow
        drawRect(
            brush = Brush.verticalGradient(
                0.0f to Color.Transparent,
                0.5f to Color(0x334A78B8),
                1.0f to Color(0x6634598F),
                startY = horizonY - block * 14f,
                endY = horizonY + block * 4f,
            ),
            topLeft = Offset(0f, horizonY - block * 14f),
            size = Size(w, block * 18f),
        )

        // 5. Smoothly Drifting 2D Blocky Night Clouds
        if (source.showClouds) {
            for (cloud in clouds) {
                drawMinecraftCloud(cloud, w, h, block, timeSeconds)
            }
        }

        // 6. Layer 1: Distant Mountain Silhouettes (slow parallax)
        val farBaseY = horizonY + block * 4f
        drawSteppedTerrain(
            heights = farMountainHeights,
            baseY = farBaseY,
            block = block,
            scrollCols = timeSeconds * 0.28f,
            fillColor = Color(0xFF121E36),
            topRimColor = Color(0xFF2B446E),
        )

        // 7. Layer 2: Mid-ground Hill Silhouettes + 6 Minecraft Trees (medium parallax)
        val midBaseY = horizonY + block * 10f
        val midScrollCols = timeSeconds * 0.55f
        drawSteppedTerrain(
            heights = midHillHeights,
            baseY = midBaseY,
            block = block,
            scrollCols = midScrollCols,
            fillColor = Color(0xFF0B1324),
            topRimColor = Color(0xFF1F3354),
        )
        drawMountainTrees(
            heights = midHillHeights,
            treeColumns = midHillTreeCols,
            baseY = midBaseY,
            block = block,
            scrollCols = midScrollCols,
            scaleFactor = 1.0f,
            treeColor = Color(0xFF0B1324),
            rimColor = Color(0xFF243B61),
        )

        // 8. Layer 3: Foreground Terrain Silhouette (foreground parallax)
        val nearBaseY = h
        drawSteppedTerrain(
            heights = nearGroundHeights,
            baseY = nearBaseY,
            block = block,
            scrollCols = timeSeconds * 0.95f,
            fillColor = Color(0xFF050912),
            topRimColor = Color(0xFF142238),
        )
    }
}

private fun DrawScope.drawMinecraftMoon(
    centerX: Float,
    centerY: Float,
    block: Float,
    timeSeconds: Float,
) {
    val pulse = 1f + 0.035f * sin(timeSeconds * 1.5f)

    // Outer blocky moonlight aura rings
    val aura3 = block * 17f * pulse
    drawRect(
        color = Color(0x1470A8FF),
        topLeft = Offset(centerX - aura3 / 2f, centerY - aura3 / 2f),
        size = Size(aura3, aura3),
    )

    val aura2 = block * 13f * pulse
    drawRect(
        color = Color(0x248CBEFF),
        topLeft = Offset(centerX - aura2 / 2f, centerY - aura2 / 2f),
        size = Size(aura2, aura2),
    )

    val aura1 = block * 10f
    drawRect(
        color = Color(0x3DB8DAFF),
        topLeft = Offset(centerX - aura1 / 2f, centerY - aura1 / 2f),
        size = Size(aura1, aura1),
    )

    // Main square moon body (8x8 blocks)
    val moonSize = block * 8f
    val left = centerX - moonSize / 2f
    val top = centerY - moonSize / 2f
    drawRect(
        color = Color(0xFFDCEAFF),
        topLeft = Offset(left, top),
        size = Size(moonSize, moonSize),
    )

    // Inner bright lunar core (6x6 blocks)
    val moonCore = block * 6f
    drawRect(
        color = Color(0xFFF4F9FF),
        topLeft = Offset(centerX - moonCore / 2f, centerY - moonCore / 2f),
        size = Size(moonCore, moonCore),
    )

    // Classic blocky Minecraft moon crater pixels
    drawRect(
        color = Color(0xFFB3C9EA),
        topLeft = Offset(left + block * 1f, top + block * 1f),
        size = Size(block * 2f, block * 2f),
    )
    drawRect(
        color = Color(0xFF9AB4DC),
        topLeft = Offset(left + block * 5f, top + block * 2f),
        size = Size(block * 2f, block * 1.5f),
    )
    drawRect(
        color = Color(0xFFAEC4E6),
        topLeft = Offset(left + block * 2f, top + block * 5f),
        size = Size(block * 2.5f, block * 2f),
    )
    drawRect(
        color = Color(0xFF8CA7D1),
        topLeft = Offset(left + block * 5f, top + block * 5f),
        size = Size(block * 1.5f, block * 1.5f),
    )
}

private fun DrawScope.drawMinecraftCloud(
    cloud: CloudSpec,
    screenWidth: Float,
    screenHeight: Float,
    block: Float,
    timeSeconds: Float,
) {
    val unit = block * 1.45f * cloud.scale
    val cloudPixelWidth = 15f * unit
    val totalSpan = screenWidth + cloudPixelWidth * 2f
    val rawX = ((cloud.baseXFrac * totalSpan) + timeSeconds * cloud.speed * screenWidth) % totalSpan
    val startX = rawX - cloudPixelWidth
    val startY = cloud.yFrac * screenHeight + sin(timeSeconds * 0.5f + cloud.baseXFrac * 8f) * (block * 0.2f)

    val puffs = cloud.blocks
    // Pass 1: 3D side/bottom depth shadow block behind the cloud silhouette
    var i = 0
    while (i + 3 < puffs.size) {
        val col = puffs[i]
        val bw = puffs[i + 1]
        val topRow = puffs[i + 2]
        val bottomRow = puffs[i + 3]

        val rx = startX + col * unit + unit * 0.28f
        val ry = startY + topRow * unit + unit * 0.34f
        val rw = bw * unit
        val rh = (bottomRow - topRow) * unit

        drawRect(
            color = Color(0x9935507D),
            topLeft = Offset(rx, ry),
            size = Size(rw, rh),
        )
        i += 4
    }

    // Pass 2: Cohesive moonlit cloud body + top lunar highlight + bottom shadow base
    i = 0
    while (i + 3 < puffs.size) {
        val col = puffs[i]
        val bw = puffs[i + 1]
        val topRow = puffs[i + 2]
        val bottomRow = puffs[i + 3]

        val rx = startX + col * unit
        val ry = startY + topRow * unit
        val rw = bw * unit + 0.8f
        val rh = (bottomRow - topRow) * unit

        // Main luminous moonlit cloud puff
        drawRect(
            color = Color(0xD9B4CEF0),
            topLeft = Offset(rx, ry),
            size = Size(rw, rh),
        )
        // Bright lunar-white top crest (only at the top of this vertical puff column—never cutting across the middle!)
        drawRect(
            color = Color(0xE6EEF6FF),
            topLeft = Offset(rx, ry),
            size = Size(rw, unit * 0.42f),
        )
        // Soft slate-blue underbelly along the flat bottom of the cloud
        drawRect(
            color = Color(0xD97899C8),
            topLeft = Offset(rx, ry + rh - unit * 0.45f),
            size = Size(rw, unit * 0.45f),
        )
        i += 4
    }
}

private fun DrawScope.drawSteppedTerrain(
    heights: IntArray,
    baseY: Float,
    block: Float,
    scrollCols: Float,
    fillColor: Color,
    topRimColor: Color,
) {
    val cols = heights.size
    val colWidth = size.width / cols
    val width = colWidth + 1.2f
    val baseIdx = kotlin.math.floor(scrollCols).toInt()
    val frac = scrollCols - baseIdx

    for (screenStep in -1..cols) {
        val leftX = (screenStep - frac) * colWidth
        val c = (screenStep + baseIdx).mod(cols)
        val hBlocks = heights[c]
        val topY = baseY - hBlocks * block

        drawRect(
            color = fillColor,
            topLeft = Offset(leftX, topY),
            size = Size(width, size.height - topY),
        )
        drawRect(
            color = topRimColor,
            topLeft = Offset(leftX, topY),
            size = Size(width, block * 0.75f),
        )
    }
}

private fun DrawScope.drawMountainTrees(
    heights: IntArray,
    treeColumns: IntArray,
    baseY: Float,
    block: Float,
    scrollCols: Float,
    scaleFactor: Float,
    treeColor: Color,
    rimColor: Color,
) {
    val cols = heights.size
    val colWidth = size.width / cols
    val b = block * scaleFactor
    val baseIdx = kotlin.math.floor(scrollCols).toInt()
    val frac = scrollCols - baseIdx

    for (screenStep in -4..(cols + 3)) {
        val c = (screenStep + baseIdx).mod(cols)
        val treeIdx = treeColumns.indexOf(c)
        if (treeIdx < 0) continue

        val groundTopY = baseY - heights[c] * block
        val centerX = (screenStep - frac + 0.5f) * colWidth
        drawSingleTree(treeIdx, centerX, groundTopY, b, treeColor, rimColor)
    }
}

private fun DrawScope.drawSingleTree(
    idx: Int,
    centerX: Float,
    groundTopY: Float,
    b: Float,
    treeColor: Color,
    rimColor: Color,
) {
    // Trunk
    val trunkW = b * 1.1f
    val trunkH = b * 3.5f
    drawRect(
        color = treeColor,
        topLeft = Offset(centerX - trunkW / 2f, groundTopY - trunkH),
        size = Size(trunkW, trunkH + b),
    )

    if (idx % 2 == 0) {
        // Classic blocky Oak tree canopy silhouette
        val lowW = b * 4.6f
        val lowH = b * 2.2f
        val lowY = groundTopY - trunkH - lowH * 0.6f
        drawRect(
            color = treeColor,
            topLeft = Offset(centerX - lowW / 2f, lowY),
            size = Size(lowW, lowH),
        )
        drawRect(
            color = rimColor,
            topLeft = Offset(centerX - lowW / 2f, lowY),
            size = Size(lowW, b * 0.45f),
        )

        val topW = b * 2.8f
        val topH = b * 1.6f
        val topY = lowY - topH + b * 0.2f
        drawRect(
            color = treeColor,
            topLeft = Offset(centerX - topW / 2f, topY),
            size = Size(topW, topH),
        )
        drawRect(
            color = rimColor,
            topLeft = Offset(centerX - topW / 2f, topY),
            size = Size(topW, b * 0.45f),
        )
    } else {
        // Classic tiered Spruce tree silhouette
        for (tier in 0..2) {
            val tierW = b * (4.4f - tier * 1.2f)
            val tierH = b * 1.5f
            val tierY = groundTopY - trunkH - tier * (b * 1.2f)
            drawRect(
                color = treeColor,
                topLeft = Offset(centerX - tierW / 2f, tierY),
                size = Size(tierW, tierH),
            )
            drawRect(
                color = rimColor,
                topLeft = Offset(centerX - tierW / 2f, tierY),
                size = Size(tierW, b * 0.4f),
            )
        }
    }
}

// =========================================================================================
// Custom Media Background Implementations (Image / GIF / Video) for future customization
// =========================================================================================

@Composable
private fun CustomImageBackground(
    modifier: Modifier,
    source: BackgroundSource.Image,
) {
    val context = LocalContext.current
    var bitmap by remember(source) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loadFailed by remember(source) { mutableStateOf(false) }

    LaunchedEffect(source) {
        loadFailed = false
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    source.uri != null -> context.contentResolver.openInputStream(source.uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                    source.resId != null -> BitmapFactory.decodeResource(context.resources, source.resId)
                    source.assetPath != null -> context.assets.open(source.assetPath).use {
                        BitmapFactory.decodeStream(it)
                    }
                    else -> null
                }
            }.getOrNull()
        }
        if (bitmap == null) loadFailed = true
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Medium,
            modifier = modifier,
        )
    } else if (loadFailed) {
        MinecraftSunsetBackground(modifier = modifier)
    }
}

@Suppress("DEPRECATION")
@Composable
private fun CustomGifBackground(
    modifier: Modifier,
    source: BackgroundSource.Gif,
) {
    val context = LocalContext.current

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        var drawable by remember(source) { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
        var failed by remember(source) { mutableStateOf(false) }

        LaunchedEffect(source) {
            failed = false
            val decoded = withContext(Dispatchers.IO) {
                runCatching {
                    val decoderSource = when {
                        source.uri != null -> ImageDecoder.createSource(context.contentResolver, source.uri)
                        source.resId != null -> ImageDecoder.createSource(context.resources, source.resId)
                        source.assetPath != null -> ImageDecoder.createSource(context.assets, source.assetPath)
                        else -> null
                    }
                    decoderSource?.let { ImageDecoder.decodeDrawable(it) }
                }.getOrNull()
            }
            if (decoded != null) {
                drawable = decoded
            } else {
                failed = true
            }
        }

        val currentDrawable = drawable
        if (currentDrawable != null) {
            AndroidView(
                modifier = modifier,
                factory = { ctx ->
                    android.widget.ImageView(ctx).apply {
                        scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = { imageView ->
                    imageView.setImageDrawable(currentDrawable)
                    if (currentDrawable is android.graphics.drawable.AnimatedImageDrawable) {
                        currentDrawable.start()
                    }
                },
            )
        } else if (failed) {
            MinecraftSunsetBackground(modifier = modifier)
        }
    } else {
        // API 24..27 fallback using android.graphics.Movie
        val movie = remember(source) {
            runCatching {
                val stream: InputStream? = when {
                    source.uri != null -> context.contentResolver.openInputStream(source.uri)
                    source.resId != null -> context.resources.openRawResource(source.resId)
                    source.assetPath != null -> context.assets.open(source.assetPath)
                    else -> null
                }
                stream?.use { android.graphics.Movie.decodeStream(it) }
            }.getOrNull()
        }

        if (movie != null && movie.width() > 0 && movie.height() > 0) {
            var elapsedMs by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(movie) {
                var startNanos = -1L
                while (isActive) {
                    withFrameNanos { nanos ->
                        if (startNanos < 0L) startNanos = nanos
                        elapsedMs = ((nanos - startNanos) / 1_000_000f)
                    }
                }
            }
            Canvas(modifier = modifier) {
                val duration = movie.duration().takeIf { it > 0 } ?: 1000
                movie.setTime((elapsedMs.toLong() % duration).toInt())
                val scale = maxOf(size.width / movie.width(), size.height / movie.height())
                val dx = (size.width - movie.width() * scale) / 2f
                val dy = (size.height - movie.height() * scale) / 2f
                drawContext.canvas.nativeCanvas.apply {
                    save()
                    translate(dx, dy)
                    scale(scale, scale)
                    movie.draw(this, 0f, 0f)
                    restore()
                }
            }
        } else {
            MinecraftSunsetBackground(modifier = modifier)
        }
    }
}

@Composable
private fun CustomVideoBackground(
    modifier: Modifier,
    source: BackgroundSource.Video,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var failed by remember(source) { mutableStateOf(false) }
    val playerHolder = remember { arrayOfNulls<MediaPlayer>(1) }

    DisposableEffect(lifecycleOwner, source) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching { playerHolder[0]?.start() }
                Lifecycle.Event.ON_PAUSE -> runCatching {
                    if (playerHolder[0]?.isPlaying == true) playerHolder[0]?.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { playerHolder[0]?.release() }
            playerHolder[0] = null
        }
    }

    if (failed) {
        MinecraftSunsetBackground(modifier = modifier)
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceView(ctx).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        runCatching {
                            val mp = MediaPlayer()
                            playerHolder[0] = mp
                            mp.setSurface(holder.surface)
                            mp.isLooping = true
                            mp.setVolume(0f, 0f)
                            when {
                                source.uri != null -> mp.setDataSource(ctx, source.uri)
                                source.rawResId != null -> {
                                    val afd = ctx.resources.openRawResourceFd(source.rawResId)
                                        ?: error("Cannot open raw resource ${source.rawResId}")
                                    afd.use {
                                        mp.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                                    }
                                }
                                source.assetPath != null -> {
                                    val afd = ctx.assets.openFd(source.assetPath)
                                    afd.use {
                                        mp.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                                    }
                                }
                                else -> error("No video source specified")
                            }
                            mp.setOnPreparedListener { it.start() }
                            mp.setOnErrorListener { _, _, _ ->
                                failed = true
                                true
                            }
                            mp.prepareAsync()
                        }.onFailure {
                            failed = true
                        }
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        runCatching { playerHolder[0]?.release() }
                        playerHolder[0] = null
                    }
                })
            }
        },
    )
}
