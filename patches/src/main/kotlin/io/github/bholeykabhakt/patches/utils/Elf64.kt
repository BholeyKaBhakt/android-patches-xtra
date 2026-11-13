package io.github.bholeykabhakt.patches.utils

import app.morphe.patcher.patch.PatchException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal little-endian ELF64 reader for locating native patch sites by symbol instead of by
 * per-version offset. Only what the patches need: dynamic symbols, PLT stubs of imports, and
 * vaddr ↔ file-offset mapping through the PT_LOAD segments.
 */
class Elf64(val bytes: ByteArray) {
    class Symbol(val name: String, val vaddr: Long, val size: Long)

    private class Section(val name: String, val type: Int, val addr: Long, val offset: Long, val size: Long, val link: Int)

    private class Segment(val offset: Long, val vaddr: Long, val filesz: Long)

    private val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private val sections: List<Section>
    private val segments: List<Segment>

    init {
        if (bytes.size < 64 || buf.getInt(0) != 0x464C457F) throw PatchException("not an ELF file")
        if (bytes[4].toInt() != 2 || bytes[5].toInt() != 1) throw PatchException("not a little-endian ELF64 file")

        val phoff = buf.getLong(0x20)
        val phentsize = buf.getShort(0x36).toInt() and 0xFFFF
        val phnum = buf.getShort(0x38).toInt() and 0xFFFF
        segments = (0 until phnum).mapNotNull { i ->
            val p = (phoff + i * phentsize).toInt()
            if (buf.getInt(p) != PT_LOAD) null
            else Segment(buf.getLong(p + 0x08), buf.getLong(p + 0x10), buf.getLong(p + 0x20))
        }

        val shoff = buf.getLong(0x28)
        val shentsize = buf.getShort(0x3A).toInt() and 0xFFFF
        val shnum = buf.getShort(0x3C).toInt() and 0xFFFF
        val shstrndx = buf.getShort(0x3E).toInt() and 0xFFFF
        fun header(i: Int) = (shoff + i * shentsize).toInt()
        val names = buf.getLong(header(shstrndx) + 0x18)
        sections = (0 until shnum).map { i ->
            val h = header(i)
            Section(
                name = cString(names + (buf.getInt(h).toLong() and 0xFFFFFFFFL)),
                type = buf.getInt(h + 0x04),
                addr = buf.getLong(h + 0x10),
                offset = buf.getLong(h + 0x18),
                size = buf.getLong(h + 0x20),
                link = buf.getInt(h + 0x28),
            )
        }
    }

    private val dynsym by lazy {
        val symtab = sections.firstOrNull { it.type == SHT_DYNSYM } ?: throw PatchException("ELF has no .dynsym")
        val strtab = sections[symtab.link].offset
        (0 until (symtab.size / 24).toInt()).map { i ->
            val s = (symtab.offset + i * 24L).toInt()
            val defined = (buf.getShort(s + 6).toInt() and 0xFFFF) != 0
            Symbol(
                name = cString(strtab + (buf.getInt(s).toLong() and 0xFFFFFFFFL)),
                vaddr = if (defined) buf.getLong(s + 8) else 0L,
                size = buf.getLong(s + 16),
            )
        }
    }

    /** A defined dynamic symbol by its mangled [name]. */
    fun symbol(name: String): Symbol? = dynsym.firstOrNull { it.name == name && it.vaddr != 0L }

    /** Vaddr of the PLT stub that calls the imported [name] (`adrp x16 ; ldr x17, [x16, #…]` → its GOT slot). */
    fun pltStub(name: String): Long? {
        val index = dynsym.indexOfFirst { it.name == name }.takeIf { it >= 0 } ?: return null
        val relaPlt = sections.firstOrNull { it.name == ".rela.plt" } ?: return null
        val got = (0 until (relaPlt.size / 24).toInt()).map { (relaPlt.offset + it * 24L).toInt() }
            .firstOrNull { (buf.getLong(it + 8) ushr 32).toInt() == index }
            ?.let { buf.getLong(it) } ?: return null
        val plt = sections.firstOrNull { it.name == ".plt" } ?: return null
        var pc = plt.addr
        while (pc + 8 <= plt.addr + plt.size) {
            val adrp = word(pc)
            val ldr = word(pc + 4)
            if (Arm64.isAdrp(adrp) && Arm64.isLdrX64(ldr) &&
                Arm64.adrpTarget(pc, adrp) + Arm64.ldrX64Offset(ldr) == got
            ) return pc
            pc += 4
        }
        return null
    }

    fun fileOffset(vaddr: Long): Int {
        val seg = segments.firstOrNull { vaddr >= it.vaddr && vaddr < it.vaddr + it.filesz }
            ?: throw PatchException("vaddr 0x${vaddr.toString(16)} is not in a loaded segment")
        return (vaddr - seg.vaddr + seg.offset).toInt()
    }

    /** The instruction word at [vaddr]. */
    fun word(vaddr: Long): Int = buf.getInt(fileOffset(vaddr))

    /** NUL-terminated string at [vaddr]. */
    fun string(vaddr: Long): String = cString(fileOffset(vaddr).toLong())

    private fun cString(offset: Long): String {
        var end = offset.toInt()
        while (end < bytes.size && bytes[end] != 0.toByte()) end++
        return String(bytes, offset.toInt(), end - offset.toInt(), Charsets.UTF_8)
    }

    private companion object {
        const val PT_LOAD = 1
        const val SHT_DYNSYM = 11
    }
}
