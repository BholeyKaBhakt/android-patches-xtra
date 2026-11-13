package io.github.bholeykabhakt.patches

import io.github.bholeykabhakt.patches.stellarium.stellariumPlusPatches
import io.github.bholeykabhakt.patches.utils.Elf64
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Symbol-located Stellarium sites must match the offsets found by hand. Point
 * `STELLARIUM_TEST_APKS` at local .apk / .apks files or folders (path-separator list) to run it;
 * without it the test does nothing.
 */
class StellariumSymbolSitesTest {
    private val lib = "lib/arm64-v8a/libstellarium-mobile-plus_arm64-v8a.so"

    /** QVariant(bool)@plt → (getHasFeaturePLUS, hasValidLicense, getAppVariant cbz), found by hand. */
    private val known = mapOf(
        0x8ACCE0 to listOf(0x73069C, 0x738798, 0x737BAC), // 1.16.3
        0x8B6BC0 to listOf(0x739938, 0x742164, 0x741578), // 1.16.9
        0x8B69F0 to listOf(0x739748, 0x741F74, 0x741388), // 1.16.10
    )

    private fun inputs(): List<File> =
        (System.getenv("STELLARIUM_TEST_APKS") ?: "").split(File.pathSeparator).filter { it.isNotBlank() }
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
    fun `symbol-located sites match the hand-found offsets`() {
        for (file in inputs()) {
            val bytes = library(file) ?: continue
            val ctor = Elf64(bytes).pltStub("_ZN8QVariantC1Eb")!!.toInt()
            val offsets = assertNotNull(known[ctor], "${file.name}: no hand-found offsets for this build")
            val sites = stellariumPlusPatches(bytes)
            assertEquals(offsets, sites.map { it.offset }, file.name)
            sites.take(2).forEach { site ->
                assertEquals(0x14000000 or (((ctor - (site.offset + 8)) shr 2) and 0x3FFFFFF), site.replacement[2], file.name)
            }
            println("checked ${file.name}")
        }
    }
}
