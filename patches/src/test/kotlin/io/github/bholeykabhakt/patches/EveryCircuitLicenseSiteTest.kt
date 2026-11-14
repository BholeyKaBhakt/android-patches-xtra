package io.github.bholeykabhakt.patches

import io.github.bholeykabhakt.patches.everycircuit.everyCircuitPatches
import io.github.bholeykabhakt.patches.utils.applyBinaryPatches
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The rewritten `MMLicenseManager::enablePermanentFeatures()` must land where it was found by hand,
 * encode to the instructions checked by disassembly, and be a no-op on an already patched library.
 * Point `EVERYCIRCUIT_TEST_APKS` at local .apk / .apks files or folders (path-separator list) to run
 * it; without it the test does nothing.
 */
class EveryCircuitLicenseSiteTest {
    private val lib = "lib/arm64-v8a/libinterface.so"

    /** enablePermanentFeatures file offset → rewritten body, found by hand. */
    private val known = mapOf(
        0x18CF94 to intArrayOf( // 2.31
            0xA9422408.toInt(), 0xD283800A.toInt(), 0xF2A4C1CA.toInt(), 0xF2C04A6A.toInt(),
            0xF2E0000A.toInt(), 0x5280004B, 0x1280000C, 0xB900800C.toInt(),
            0xB900840C.toInt(), 0xEB09011F.toInt(), 0x540000A2, 0xB900390B.toInt(),
            0xF900210A.toInt(), 0x91012108.toInt(), 0x17FFFFFB, 0xD65F03C0.toInt(),
        ),
    )

    private fun inputs(): List<File> =
        (System.getenv("EVERYCIRCUIT_TEST_APKS") ?: "").split(File.pathSeparator).filter { it.isNotBlank() }
            .map(::File)
            .flatMap { if (it.isDirectory) it.listFiles()!!.sorted() else listOf(it) }
            .filter { it.name.endsWith(".apk") || it.name.endsWith(".apks") }

    private fun library(file: File): ByteArray? = ZipFile(file).use { zip ->
        zip.getEntry(lib)?.let { return zip.getInputStream(it).readBytes() }
        val split = zip.getEntry("split_config.arm64_v8a.apk") ?: return null
        val tmp = File.createTempFile("split", ".apk").apply { deleteOnExit() }
        zip.getInputStream(split).use { input -> tmp.outputStream().use { input.copyTo(it) } }
        ZipFile(tmp).use { apk -> apk.getEntry(lib)?.let { apk.getInputStream(it).readBytes() } }
    }

    @Test
    fun `license rewrite matches the hand-checked site and is idempotent`() {
        for (file in inputs()) {
            val bytes = library(file) ?: continue
            val site = everyCircuitPatches(bytes).single()
            val words = assertNotNull(known[site.offset], "${file.name}: no hand-checked rewrite at 0x${site.offset.toString(16)}")
            assertTrue(words.contentEquals(site.replacement), file.name)
            assertEquals(1, bytes.applyBinaryPatches(listOf(site), "stale"), file.name)
            assertTrue(everyCircuitPatches(bytes).isEmpty(), "${file.name}: second pass should be a no-op")
            println("checked ${file.name}")
        }
    }
}
