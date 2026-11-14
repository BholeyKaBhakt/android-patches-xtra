package io.github.bholeykabhakt.patches.utils

import app.morphe.patcher.patch.PatchException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NativePatchingTest {
    private fun lib(size: Int, vararg words: Pair<Int, Int>) = ByteArray(size) { 0x5A }.also { bytes ->
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        words.forEach { (offset, word) -> buf.putInt(offset, word) }
    }

    private fun ByteArray.word(offset: Int) = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).getInt(offset)

    @Test
    fun `B encodes forward and backward displacements`() {
        // Expected values computed independently (Python) from the ARM64 imm26 definition.
        assertEquals(0x14000002, Arm64.b(pc = 0, target = 8))
        assertEquals(0x17FFFFFC, Arm64.b(pc = 0x1000, target = 0x0FF0))
    }

    @Test
    fun `B rejects misaligned and out-of-range targets`() {
        assertFailsWith<PatchException> { Arm64.b(pc = 0, target = 6) }
        assertFailsWith<PatchException> { Arm64.b(pc = 0, target = 1 shl 27) }
    }

    @Test
    fun `writes the replacement when the expected words are present`() {
        val bytes = lib(32, 8 to 0x11111111, 12 to 0x22222222)
        val patch = BinaryPatch("f", 8, intArrayOf(0x11111111, 0x22222222), intArrayOf(Arm64.MOV_W0_1, Arm64.RET, Arm64.NOP))
        assertEquals(1, bytes.applyBinaryPatches(listOf(patch), "stale"))
        assertEquals(listOf(Arm64.MOV_W0_1, Arm64.RET, Arm64.NOP), listOf(8, 12, 16).map { bytes.word(it) })
        assertEquals(0x5A5A5A5A, bytes.word(4)) // untouched neighbour
    }

    @Test
    fun `re-applying is a no-op`() {
        val bytes = lib(16, 4 to 0x11111111)
        val patches = listOf(BinaryPatch("f", 4, intArrayOf(0x11111111), intArrayOf(Arm64.RET)))
        bytes.applyBinaryPatches(patches, "stale")
        val once = bytes.copyOf()
        assertEquals(0, bytes.applyBinaryPatches(patches, "stale"))
        assertContentEquals(once, bytes)
    }

    @Test
    fun `a stale site fails and leaves the buffer untouched`() {
        val bytes = lib(16, 0 to 0x11111111, 4 to 0x33333333)
        val original = bytes.copyOf()
        val patches = listOf(
            BinaryPatch("good", 0, intArrayOf(0x11111111), intArrayOf(Arm64.RET)),
            BinaryPatch("stale", 4, intArrayOf(0x22222222), intArrayOf(Arm64.RET)),
        )
        val e = assertFailsWith<PatchException> { bytes.applyBinaryPatches(patches, "table is stale") }
        assertContains(e.message!!, "stale @ 0x4")
        assertContains(e.message!!, "found [0x33333333]")
        assertContentEquals(original, bytes) // validated before writing: no half-patched library
    }

    @Test
    fun `a site past the end of the library fails`() {
        val patch = BinaryPatch("f", 8, intArrayOf(0), intArrayOf(Arm64.MOV_W0_1, Arm64.RET))
        assertFailsWith<PatchException> { lib(12).applyBinaryPatches(listOf(patch), "stale") }
    }

    @Test
    fun `retHead asserts the prologue and writes a single RET`() {
        val p = retHead("f", 0x10, 0xA9BE57FE.toInt(), 0xA9014FF4.toInt())
        assertContentEquals(intArrayOf(0xA9BE57FE.toInt(), 0xA9014FF4.toInt()), p.expected)
        assertContentEquals(intArrayOf(Arm64.RET), p.replacement)
    }

    @Test
    fun `misaligned offsets are rejected when the table is built`() {
        assertFailsWith<IllegalArgumentException> { BinaryPatch("f", 2, intArrayOf(0), intArrayOf(0)) }
    }

    @Test
    fun `forVersion lists the supported versions`() {
        val table = mapOf("1.0" to 1, "1.1" to 2)
        assertEquals(2, table.forVersion("App", "1.1", "hint"))
        val e = assertFailsWith<PatchException> { table.forVersion("App", "2.0", "Add offsets.") }
        assertEquals("App version 2.0 is not supported. Supported: 1.0, 1.1. Add offsets.", e.message)
    }
}
