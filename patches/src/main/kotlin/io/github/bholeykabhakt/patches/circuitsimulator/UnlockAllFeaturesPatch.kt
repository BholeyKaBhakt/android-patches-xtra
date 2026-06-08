package io.github.bholeykabhakt.patches.circuitsimulator

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction22t
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import io.github.bholeykabhakt.patches.utils.logMatch

/**
 * Unlocks every paid feature in Proto Circuit Simulator. Each feature gate
 * (component visibility, export, import, themes, scope configs, print, extras)
 * branches on the same shape:
 *
 *   sget-object vR, Lenum;->OK_FIELD:Lenum;
 *   if-ne <statusReg>, vR, :locked_label
 *
 * where the enum has a value named "OK" plus per-feature `MISSING_*` reasons.
 * We walk every method and, wherever this pair appears (with up to a few
 * intervening instructions), rewrite the `if-ne` to compare a register to
 * itself — vacuously false, never branches, unlocked path runs unconditionally.
 *
 * The per-addon "is-owned" boolean getter is deliberately left alone: every
 * feature gate uses the enum, not the boolean, and forcing the boolean true
 * makes the launcher hide owned addons from its buyable list.
 */
@Suppress("unused")
val unlockAllFeaturesPatch = bytecodePatch(
    name = "Unlock All Features",
) {
    compatibleWith(
        Compatibility(
            packageName = "com.proto.circuitsimulator",
            name = "Circuit Simulator"
        )
    )

    execute {
        val iapEnum = IapStatusEnumFingerprint.logMatch.classDef
        val okField = findOkEnumFieldName(iapEnum)
            ?: throw PatchException("No \"OK\" entry in ${iapEnum.type} <clinit>")

        var rewrites = 0
        classDefForEach { classDef ->
            mutableClassDefBy(classDef.type).methods.forEach methodLoop@{ method ->
                val impl = method.implementation ?: return@methodLoop
                val n = impl.instructions.size

                for (i in 0 until n - 1) {
                    val sget = impl.getInstruction(i)
                    if (sget.opcode != Opcode.SGET_OBJECT) continue
                    val fref =
                        (sget as ReferenceInstruction).reference as? FieldReference ?: continue
                    if (fref.definingClass != iapEnum.type || fref.name != okField) continue
                    val okReg = (sget as OneRegisterInstruction).registerA

                    // Tolerate small drift (e.g. an intervening `const/4`) between the
                    // OK load and the if-ne consuming it.
                    for (j in (i + 1)..minOf(i + 4, n - 1)) {
                        val ifNe = impl.getInstruction(j) as? BuilderInstruction22t ?: continue
                        if (ifNe.opcode != Opcode.IF_NE) continue
                        if (ifNe.registerA != okReg && ifNe.registerB != okReg) continue

                        method.replaceInstruction(
                            j,
                            BuilderInstruction22t(
                                Opcode.IF_NE,
                                ifNe.registerA,
                                ifNe.registerA,
                                ifNe.target
                            ),
                        )
                        rewrites++
                        break
                    }
                }
            }
        }

        if (rewrites == 0) throw PatchException("No `if-ne` gates against ${iapEnum.type} found")
    }
}

/**
 * Scan `<clinit>` for the `sput-object` that follows `const-string "OK"` — the
 * enum field holding the "unlocked" entry. The field letter (`D` in v1.48) can
 * shift between releases, so we discover it instead of hard-coding.
 */
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
