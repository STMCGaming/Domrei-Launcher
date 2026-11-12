package com.domrei.skinview.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BillboardMeshTest {

    @Test
    fun verifyBillboardMeshVertexCountAndBounds() {
        val mesh = BillboardMesh()
        assertEquals(6, mesh.vertexCount)
        val buffer = mesh.buffer.duplicate().apply { position(0) }
        val floats = FloatArray(mesh.vertexCount * BoxMesh.STRIDE_FLOATS)
        buffer.get(floats)

        // Stride is 8 floats: x, y, z, u, v, nx, ny, nz
        val xs = (0 until mesh.vertexCount).map { floats[it * 8] }
        val ys = (0 until mesh.vertexCount).map { floats[it * 8 + 1] }
        val zs = (0 until mesh.vertexCount).map { floats[it * 8 + 2] }

        assertEquals(-0.5f, xs.min(), 1e-4f)
        assertEquals(0.5f, xs.max(), 1e-4f)
        assertEquals(-0.5f, ys.min(), 1e-4f)
        assertEquals(0.5f, ys.max(), 1e-4f)
        assertEquals(0f, zs.min(), 1e-4f)
        assertEquals(0f, zs.max(), 1e-4f)
    }
}
