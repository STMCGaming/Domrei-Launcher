package com.domrei.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderListTest {

    @Test
    fun testReorderMoveDown() {
        val list = listOf("A", "B", "C", "D")
        val result = list.reorder(0, 2)
        assertEquals(listOf("B", "C", "A", "D"), result)
    }

    @Test
    fun testReorderMoveUp() {
        val list = listOf("A", "B", "C", "D")
        val result = list.reorder(3, 0)
        assertEquals(listOf("D", "A", "B", "C"), result)
    }

    @Test
    fun testReorderMoveToBottom() {
        val list = listOf("A", "B", "C")
        val result = list.reorder(0, 2)
        assertEquals(listOf("B", "C", "A"), result)
    }

    @Test
    fun testReorderMoveToTop() {
        val list = listOf("A", "B", "C")
        val result = list.reorder(2, 0)
        assertEquals(listOf("C", "A", "B"), result)
    }

    @Test
    fun testReorderSameIndex() {
        val list = listOf("A", "B", "C")
        val result = list.reorder(1, 1)
        assertEquals(list, result)
    }

    @Test
    fun testReorderOutOfBounds() {
        val list = listOf("A", "B")
        assertEquals(list, list.reorder(-1, 0))
        assertEquals(list, list.reorder(0, 5))
    }

    @Test
    fun testReorderSingleElement() {
        val list = listOf("A")
        assertEquals(list, list.reorder(0, 0))
    }
}
