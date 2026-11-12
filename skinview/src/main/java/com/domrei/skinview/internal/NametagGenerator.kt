package com.domrei.skinview.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Renders a crisp Minecraft-like player nametag:
 * - Dark translucent background plate (RGBA 0x66000000 / ~40% opacity)
 * - Crisp text with classic Minecraft dark shadow offset (+2px #3F3F3F)
 * - White foreground text (#FFFFFF)
 */
internal object NametagGenerator {

    fun create(text: String): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 56f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            color = Color.WHITE
        }

        val textWidth = paint.measureText(text)
        val fontMetrics = paint.fontMetrics
        val textHeight = fontMetrics.bottom - fontMetrics.top

        val padX = 28f
        val padY = 14f
        val width = (textWidth + padX * 2).toInt().coerceAtLeast(1)
        val height = (textHeight + padY * 2).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Minecraft dark translucent background plate
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(125, 0, 0, 0)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Minecraft classic text drop shadow
        val shadowPaint = Paint(paint).apply {
            color = Color.rgb(63, 63, 63)
        }
        val baseline = padY - fontMetrics.top
        canvas.drawText(text, padX + 4f, baseline + 4f, shadowPaint)

        // White foreground text
        canvas.drawText(text, padX, baseline, paint)

        return bitmap
    }
}
