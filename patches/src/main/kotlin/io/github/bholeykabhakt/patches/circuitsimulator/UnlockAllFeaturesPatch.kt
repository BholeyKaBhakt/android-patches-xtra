package io.github.bholeykabhakt.patches.circuitsimulator

import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.BuilderSwitchPayload
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction22t
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method as MethodLike
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_CIRCUIT_SIMULATOR
import io.github.bholeykabhakt.patches.utils.logMatch

private const val FIRESTORE = "Lcom/google/firebase/firestore/FirebaseFirestore;"

/**
 * Every paid feature compares an IAP-status enum to `OK` and branches to the locked path on
 * mismatch; each such `if-ne` is rewritten to compare a register to itself (never taken).
 * Where present (1.53+), the pack-ownership query the status producers call is also forced true,
 * so they emit a genuine `OK`. That covers gates that test `== OK` without an `if-ne`.
 */
@Suppress("unused")
val unlockAllFeaturesPatch = patch(
    name = "Unlock All Features",
) {
    compatibleWith(COMPATIBILITY_CIRCUIT_SIMULATOR)

    dependsOn(disableAnalyticsPatch)

    execute {
        val iapEnum = IapStatusEnumFingerprint.logMatch.classDef
        val okField = findOkEnumFieldName(iapEnum)
            ?: throw PatchException("No \"OK\" entry in ${iapEnum.type} <clinit>")

        val isOkLoad = { insn: Instruction ->
            insn.opcode == Opcode.SGET_OBJECT &&
                ((insn as ReferenceInstruction).reference as? FieldReference)
                    .let { it?.definingClass == iapEnum.type && it.name == okField }
        }

        var rewrites = 0
        classDefForEach { classDef ->
            if (classDef.methods.none { m -> m.implementation?.instructions?.any(isOkLoad) == true }) {
                return@classDefForEach
            }
            mutableClassDefBy(classDef.type).methods.forEach methodLoop@{ method ->
                val impl = method.implementation ?: return@methodLoop
                if (impl.instructions.none(isOkLoad)) return@methodLoop
                for (j in okGates(impl, isOkLoad)) {
                    val ifNe = impl.getInstruction(j) as BuilderInstruction22t
                    method.replaceInstruction(
                        j,
                        BuilderInstruction22t(Opcode.IF_NE, ifNe.registerA, ifNe.registerA, ifNe.target),
                    )
                    rewrites++
                }
            }
        }

        if (rewrites == 0) throw PatchException("No `if-ne` gates against ${iapEnum.type} found")

        forcePackOwnership()
    }
}

/**
 * `(packEnum, Continuation) → Object` on the Firestore-backed purchase store: the suspend
 * "is this pack owned" query behind every status producer. Skipped unless exactly one matches.
 */
private fun forcePackOwnership() {
    val packEnum = AddonPackEnumFingerprint.matchOrNull()?.classDef?.type ?: return
    fun MethodLike.isOwnershipQuery() = returnType == "Ljava/lang/Object;" &&
        parameterTypes.size == 2 && parameterTypes[0].toString() == packEnum
    val owners = mutableListOf<String>()
    classDefForEach { classDef ->
        if (classDef.fields.any { it.type == FIRESTORE } && classDef.methods.any { it.isOwnershipQuery() }) {
            owners += classDef.type
        }
    }
    if (owners.size != 1) return
    val queries = mutableClassDefBy(owners.single()).methods.filter { it.isOwnershipQuery() }
    if (queries.size != 1) return
    queries.single().addInstructions(
        0,
        """
            sget-object v0, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
            return-object v0
        """,
    )
}

/**
 * Indices of `if-ne` instructions where one operand holds the OK entry on every path reaching it
 * (forward must-dataflow over branches, gotos and switches). The OK load is often hoisted to the
 * top of a coroutine and shared by several switch cases, so a straight-line scan isn't enough.
 */
private fun okGates(impl: MutableMethodImplementation, isOkLoad: (Instruction) -> Boolean): List<Int> {
    val insns = impl.instructions
    val n = insns.size

    fun successors(i: Int): List<Int> {
        val insn = insns[i]
        val out = mutableListOf<Int>()
        if (insn.opcode.canContinue() && i + 1 < n) out += i + 1
        if (insn is BuilderOffsetInstruction) {
            val target = insn.target.location.index
            val payload = insns.getOrNull(target)
            if (payload is BuilderSwitchPayload) {
                payload.switchElements.forEach { out += it.target.location.index }
            } else {
                out += target
            }
        }
        return out
    }

    val inOk = arrayOfNulls<Set<Int>>(n) // null = not reached yet
    inOk[0] = emptySet()
    val work = ArrayDeque(listOf(0))
    while (work.isNotEmpty()) {
        val i = work.removeFirst()
        val insn = insns[i]
        var out = inOk[i]!!
        if (isOkLoad(insn)) {
            out = out + (insn as OneRegisterInstruction).registerA
        } else if (insn.opcode.setsRegister() && insn is OneRegisterInstruction) {
            val dst = insn.registerA
            out = out - dst - (if (insn.opcode.setsWideRegister()) dst + 1 else dst)
        }
        for (s in successors(i)) {
            val merged = inOk[s]?.intersect(out) ?: out
            if (merged != inOk[s]) {
                inOk[s] = merged
                work += s
            }
        }
    }

    return insns.indices.filter { j ->
        val insn = insns[j]
        val ok = inOk[j] ?: return@filter false
        insn.opcode == Opcode.IF_NE && insn is BuilderInstruction22t &&
            (insn.registerA in ok || insn.registerB in ok)
    }
}

/** Name of the enum field assigned right after `const-string "OK"` in `<clinit>` (obfuscated, shifts per release). */
private fun findOkEnumFieldName(enumClass: ClassDef): String? {
    val insns = enumClass.directMethods.firstOrNull { it.name == "<clinit>" }
        ?.implementation?.instructions ?: return null

    var sawOkLiteral = false
    for (insn in insns) {
        when (insn.opcode) {
            Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO -> {
                sawOkLiteral =
                    ((insn as ReferenceInstruction).reference as? StringReference)?.string == "OK"
            }

            Opcode.SPUT_OBJECT -> if (sawOkLiteral) {
                val fref = (insn as ReferenceInstruction).reference as? FieldReference
                if (fref?.definingClass == enumClass.type && fref.type == enumClass.type) return fref.name
            }

            else -> Unit
        }
    }
    return null
}
