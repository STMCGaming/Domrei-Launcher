package com.domrei.skinview

import android.graphics.Bitmap

/**
 * Procedurally generated placeholder skin and cape, so the viewer works without bundled assets.
 */
object DefaultSkin {

    data class Palette(
        val skin: Int = 0xFFC8946E.toInt(),
        val skinShade: Int = 0xFFB07E5C.toInt(),
        val hair: Int = 0xFF3B2A1E.toInt(),
        val eyeWhite: Int = 0xFFFFFFFF.toInt(),
        val eye: Int = 0xFF3355AA.toInt(),
        val mouth: Int = 0xFF7A4A3A.toInt(),
        val shirt: Int = 0xFF2BA6A6.toInt(),
        val shirtShade: Int = 0xFF208787.toInt(),
        val pants: Int = 0xFF3A3F9E.toInt(),
        val pantsShade: Int = 0xFF2F3380.toInt(),
        val shoes: Int = 0xFF5A5A5A.toInt(),
    )

    /** Creates a 64x64 skin. */
    fun create(slim: Boolean = false, palette: Palette = Palette()): Bitmap {
        val px = IntArray(64 * 64) // fully transparent
        val p = palette

        fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) {
            for (yy in y until y + h) for (xx in x until x + w) px[yy * 64 + xx] = color
        }

        /** Fills all 6 faces of a box UV layout. */
        fun box(u: Int, v: Int, w: Int, h: Int, d: Int, side: Int, top: Int = side, bottom: Int = side) {
            rect(u + d, v, w, d, top)
            rect(u + d + w, v, w, d, bottom)
            rect(u, v + d, d + w + d + w, h, side)
        }

        val armW = if (slim) 3 else 4

        // Head
        box(0, 0, 8, 8, 8, p.skin, top = p.hair, bottom = p.skinShade)
        rect(0, 8, 32, 2, p.hair)        // fringe on all four sides
        rect(0, 8, 8, 4, p.hair)         // right side of head
        rect(16, 8, 8, 4, p.hair)        // left side of head
        rect(24, 8, 8, 8, p.hair)        // back of head
        rect(8, 10, 1, 1, p.hair); rect(15, 10, 1, 1, p.hair)
        // Face (front at 8,8)
        rect(9, 12, 2, 1, p.eyeWhite); rect(10, 12, 1, 1, p.eye)
        rect(13, 12, 2, 1, p.eyeWhite); rect(13, 12, 1, 1, p.eye)
        rect(11, 13, 2, 1, p.skinShade) // nose
        rect(10, 14, 4, 1, p.mouth)

        // Body
        box(16, 16, 8, 12, 4, p.shirt, top = p.shirt, bottom = p.pants)
        rect(16, 20, 24, 1, p.shirtShade) // collar line... subtle

        // Arms: upper half shirt sleeve, rest skin
        fun arm(u: Int, v: Int) {
            box(u, v, armW, 12, 4, p.skin, top = p.shirt, bottom = p.skinShade)
            rect(u, v + 4, 4 + armW + 4 + armW, 4, p.shirt)
            rect(u, v + 7, 4 + armW + 4 + armW, 1, p.shirtShade)
        }
        arm(40, 16) // right
        arm(32, 48) // left

        // Legs: pants with shoes
        fun leg(u: Int, v: Int) {
            box(u, v, 4, 12, 4, p.pants, top = p.pants, bottom = p.shoes)
            rect(u, v + 4 + 9, 16, 3, p.shoes)
            rect(u, v + 4, 16, 1, p.pantsShade)
        }
        leg(0, 16)  // right
        leg(16, 48) // left

        return Bitmap.createBitmap(px, 64, 64, Bitmap.Config.ARGB_8888)
    }

    /** Creates a simple 64x32 cape with a border. */
    fun createCape(
        main: Int = 0xFFB02E26.toInt(),
        border: Int = 0xFFE8C547.toInt(),
    ): Bitmap {
        val px = IntArray(64 * 32)
        fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) {
            for (yy in y until y + h) for (xx in x until x + w) px[yy * 64 + xx] = color
        }
        // Cape box: u=0, v=0, w=10, h=16, d=1
        rect(1, 0, 20, 1, main)        // top + bottom
        rect(0, 1, 22, 16, main)       // sides
        // border on outer (front region at 1,1 10x16)
        rect(1, 1, 10, 1, border); rect(1, 16, 10, 1, border)
        rect(1, 1, 1, 16, border); rect(10, 1, 1, 16, border)
        rect(5, 6, 2, 6, border); rect(4, 8, 4, 2, border) // emblem
        return Bitmap.createBitmap(px, 64, 32, Bitmap.Config.ARGB_8888)
    }
}
