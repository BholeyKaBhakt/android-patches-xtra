package io.github.bholeykabhakt.patches

import io.github.bholeykabhakt.patches.skytonight.SKY_TONIGHT_PREMIUM_PATCHES
import io.github.bholeykabhakt.patches.skytonight.SKY_TONIGHT_TELEMETRY_SITES
import io.github.bholeykabhakt.patches.starwalk2.STAR_WALK_2_PREMIUM_PATCHES
import io.github.bholeykabhakt.patches.starwalk2.STAR_WALK_2_TELEMETRY_SITES
import io.github.bholeykabhakt.patches.utils.BinaryPatch
import io.github.bholeykabhakt.patches.utils.applyBinaryPatches
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Self-consistency of every per-version native offset table: applied to a synthetic library that
 * holds exactly the expected words, each table must write exactly its replacements, touch nothing
 * else, be idempotent, and never overlap two sites. (Matching the real APK is checked at patch time.)
 */
class NativeOffsetTablesTest {
    private val tables: Map<String, Map<String, List<BinaryPatch>>> = mapOf(
        "Star Walk 2 premium" to STAR_WALK_2_PREMIUM_PATCHES,
        "Star Walk 2 telemetry" to STAR_WALK_2_TELEMETRY_SITES,
        "Sky Tonight premium" to SKY_TONIGHT_PREMIUM_PATCHES,
        "Sky Tonight telemetry" to SKY_TONIGHT_TELEMETRY_SITES,
    )

    private fun span(p: BinaryPatch) = p.offset until p.offset + 4 * maxOf(p.expected.size, p.replacement.size)

    @Test
    fun `every table applies exactly its replacements`() {
        for ((table, byVersion) in tables) for ((version, patches) in byVersion) {
            val label = "$table v$version"
            val size = patches.maxOf { span(it).last } + 1 + 64
            val bytes = ByteArray(size) { 0x5A }
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            patches.forEach { p -> p.expected.forEachIndexed { i, w -> buf.putInt(p.offset + i * 4, w) } }

            val expected = bytes.copyOf()
            val expectedBuf = ByteBuffer.wrap(expected).order(ByteOrder.LITTLE_ENDIAN)
            patches.forEach { p -> p.replacement.forEachIndexed { i, w -> expectedBuf.putInt(p.offset + i * 4, w) } }

            assertEquals(patches.size, bytes.applyBinaryPatches(patches, label), "$label: sites written")
            assertContentEquals(expected, bytes, "$label: only the declared words may change")
            assertEquals(0, bytes.applyBinaryPatches(patches, label), "$label: idempotent")
        }
    }

    @Test
    fun `no table has overlapping sites`() {
        for ((table, byVersion) in tables) for ((version, patches) in byVersion) {
            val sorted = patches.sortedBy { it.offset }
            sorted.zipWithNext { a, b ->
                assertTrue(span(a).last < b.offset, "$table v$version: ${a.label} overlaps ${b.label}")
            }
        }
    }
}
