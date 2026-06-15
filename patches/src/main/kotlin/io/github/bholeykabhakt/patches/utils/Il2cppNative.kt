package io.github.bholeykabhakt.patches.utils

import app.morphe.patcher.patch.PatchException
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** ARM64 `RET` (little-endian). */
const val ARM64_RET: Int = 0xD65F03C0.toInt()

/**
 * One IL2CPP native function head to neutralise. [offset] is the file offset (Il2CppDumper `dump.cs`
 * `Offset:` column) of the function; [prologue0]/[prologue1] are its first two expected instruction
 * words, asserted before the head is overwritten so a stale offset fails loudly.
 */
data class RetSite(val name: String, val offset: Int, val prologue0: Int, val prologue1: Int)

/**
 * Overwrites the head of each [sites] entry in this IL2CPP `.so` with a bare `RET`, making the
 * function return immediately. This is only safe at a head where the asserted prologue is the
 * function's first store-pair — i.e. no callee-saved register has been pushed and there is no stack
 * frame to unwind yet. Idempotent (skips heads already `RET`), and asserts each prologue pair so an
 * outdated offset table throws instead of corrupting the binary.
 *
 * [appLabel]/[version] are only used to make the failure message actionable.
 */
fun File.writeRetHeads(appLabel: String, version: String, sites: List<RetSite>) {
    if (!exists()) throw PatchException("$path not found in APK")

    val bytes = readBytes()
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    var dirty = false
    for (site in sites) {
        if (buffer.getInt(site.offset) == ARM64_RET) continue // idempotent — already patched

        val p0 = buffer.getInt(site.offset)
        val p1 = buffer.getInt(site.offset + 4)
        if (p0 != site.prologue0 || p1 != site.prologue1) {
            throw PatchException(
                "$appLabel ${site.name}() @ 0x${
                    site.offset.toString(16).uppercase()
                } — expected prologue " +
                        "0x${site.prologue0.toUInt().toString(16)} 0x${
                            site.prologue1.toUInt().toString(16)
                        }, " +
                        "got 0x${p0.toUInt().toString(16)} 0x${
                            p1.toUInt().toString(16)
                        }; v$version offset table is stale",
            )
        }
        buffer.putInt(site.offset, ARM64_RET)
        dirty = true
    }
    if (dirty) writeBytes(bytes)
}
