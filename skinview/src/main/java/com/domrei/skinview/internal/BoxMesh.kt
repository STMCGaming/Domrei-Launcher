package com.domrei.skinview.internal

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * A textured cuboid using Minecraft's box UV layout.
 *
 * Interleaved vertex layout: position(3) uv(2) normal(3).
 * The box is centered on the origin; model faces +Z.
 */
internal class BoxMesh(
    width: Float, height: Float, depth: Float,
    u: Float, v: Float,
    textureWidth: Float, textureHeight: Float,
    inflate: Float = 0f,
) {
    val vertexCount = 36
    val buffer: FloatBuffer

    init {
        val w = width; val h = height; val d = depth
        val hx = w / 2f + inflate
        val hy = h / 2f + inflate
        val hz = d / 2f + inflate
        val data = FloatArray(vertexCount * STRIDE_FLOATS)
        var i = 0

        // corners: top-left, top-right, bottom-right, bottom-left as laid out in the texture
        fun face(
            ru: Float, rv: Float, rw: Float, rh: Float,
            tl: FloatArray, tr: FloatArray, br: FloatArray, bl: FloatArray,
            nx: Float, ny: Float, nz: Float,
        ) {
            val u0 = ru / textureWidth; val u1 = (ru + rw) / textureWidth
            val v0 = rv / textureHeight; val v1 = (rv + rh) / textureHeight
            fun put(p: FloatArray, tu: Float, tv: Float) {
                data[i++] = p[0]; data[i++] = p[1]; data[i++] = p[2]
                data[i++] = tu; data[i++] = tv
                data[i++] = nx; data[i++] = ny; data[i++] = nz
            }
            // two CCW triangles (seen from outside)
            put(tl, u0, v0); put(bl, u0, v1); put(br, u1, v1)
            put(tl, u0, v0); put(br, u1, v1); put(tr, u1, v0)
        }

        fun p(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)

        // front (+Z)
        face(u + d, v + d, w, h, p(-hx, hy, hz), p(hx, hy, hz), p(hx, -hy, hz), p(-hx, -hy, hz), 0f, 0f, 1f)
        // back (-Z)
        face(u + d + w + d, v + d, w, h, p(hx, hy, -hz), p(-hx, hy, -hz), p(-hx, -hy, -hz), p(hx, -hy, -hz), 0f, 0f, -1f)
        // right side of the model (-X)
        face(u, v + d, d, h, p(-hx, hy, -hz), p(-hx, hy, hz), p(-hx, -hy, hz), p(-hx, -hy, -hz), -1f, 0f, 0f)
        // left side of the model (+X)
        face(u + d + w, v + d, d, h, p(hx, hy, hz), p(hx, hy, -hz), p(hx, -hy, -hz), p(hx, -hy, hz), 1f, 0f, 0f)
        // top (+Y)
        face(u + d, v, w, d, p(-hx, hy, -hz), p(hx, hy, -hz), p(hx, hy, hz), p(-hx, hy, hz), 0f, 1f, 0f)
        // bottom (-Y)
        face(u + d + w, v, w, d, p(-hx, -hy, hz), p(hx, -hy, hz), p(hx, -hy, -hz), p(-hx, -hy, -hz), 0f, -1f, 0f)

        buffer = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(data); position(0) }
    }

    companion object {
        const val STRIDE_FLOATS = 8
        const val STRIDE_BYTES = STRIDE_FLOATS * 4
    }
}
