package com.domrei.skinview.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxMeshTest {

    private fun vertices(mesh: BoxMesh): List<FloatArray> {
        val b = mesh.buffer.duplicate().apply { position(0) }
        return List(mesh.vertexCount) { FloatArray(BoxMesh.STRIDE_FLOATS).also { b.get(it) } }
    }

    @Test
    fun trianglesWindCounterClockwiseOutward() {
        val mesh = BoxMesh(8f, 12f, 4f, 16f, 16f, 64f, 64f)
        vertices(mesh).chunked(3).forEach { (a, b, c) ->
            val e1 = floatArrayOf(b[0] - a[0], b[1] - a[1], b[2] - a[2])
            val e2 = floatArrayOf(c[0] - a[0], c[1] - a[1], c[2] - a[2])
            val cross = floatArrayOf(
                e1[1] * e2[2] - e1[2] * e2[1],
                e1[2] * e2[0] - e1[0] * e2[2],
                e1[0] * e2[1] - e1[1] * e2[0],
            )
            val dot = cross[0] * a[5] + cross[1] * a[6] + cross[2] * a[7]
            assertTrue("triangle winding must match its outward normal", dot > 0f)
            // vertex must lie on the face its normal points to
            val center = a[0] * a[5] + a[1] * a[6] + a[2] * a[7]
            assertTrue(center > 0f)
        }
    }

    @Test
    fun headFrontFaceUsesFaceRegion() {
        val mesh = BoxMesh(8f, 8f, 8f, 0f, 0f, 64f, 64f)
        val front = vertices(mesh).filter { it[7] == 1f }
        assertEquals(6, front.size)
        val us = front.map { it[3] * 64f }
        val vs = front.map { it[4] * 64f }
        assertEquals(8f, us.min(), 1e-4f); assertEquals(16f, us.max(), 1e-4f)
        assertEquals(8f, vs.min(), 1e-4f); assertEquals(16f, vs.max(), 1e-4f)
        // top-left of the texture region is at the model's right (-X), top (+Y)
        val topLeft = front.first { it[3] * 64f == 8f && it[4] * 64f == 8f }
        assertTrue(topLeft[0] < 0f && topLeft[1] > 0f)
    }

    @Test
    fun inflateGrowsGeometryButNotUv() {
        val mesh = BoxMesh(8f, 8f, 8f, 32f, 0f, 64f, 64f, inflate = 0.5f)
        val v = vertices(mesh)
        assertEquals(4.5f, v.maxOf { it[0] }, 1e-4f)
        assertEquals(32f / 64f, v.minOf { it[3] }, 1e-4f)
        assertEquals(64f / 64f, v.maxOf { it[3] }, 1e-4f)
    }
}
