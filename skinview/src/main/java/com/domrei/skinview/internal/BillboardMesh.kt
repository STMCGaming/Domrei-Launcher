package com.domrei.skinview.internal

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * A simple camera-facing planar quad centered at origin [-0.5..0.5, -0.5..0.5].
 * Interleaved vertex layout: position(3) uv(2) normal(3).
 */
internal class BillboardMesh {
    val vertexCount = 6
    val buffer: FloatBuffer

    init {
        val data = floatArrayOf(
            // Triangle 1
            -0.5f, -0.5f, 0f,   0f, 1f,   0f, 0f, 1f,
             0.5f, -0.5f, 0f,   1f, 1f,   0f, 0f, 1f,
             0.5f,  0.5f, 0f,   1f, 0f,   0f, 0f, 1f,
            // Triangle 2
            -0.5f, -0.5f, 0f,   0f, 1f,   0f, 0f, 1f,
             0.5f,  0.5f, 0f,   1f, 0f,   0f, 0f, 1f,
            -0.5f,  0.5f, 0f,   0f, 0f,   0f, 0f, 1f,
        )

        buffer = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(data); position(0) }
    }
}
