package io.github.bholeykabhakt.patches.all.bypassadbdetection

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction3rc
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference

private const val GLOBAL_DESC = "Landroid/provider/Settings\$Global;"
private const val SECURE_DESC = "Landroid/provider/Settings\$Secure;"
private const val SYSTEM_DESC = "Landroid/provider/Settings\$System;"

private const val GLOBAL_SHIM = "Lio/github/bholeykabhakt/extension/bypassadbdetection/GlobalShim;"
private const val SECURE_SHIM = "Lio/github/bholeykabhakt/extension/bypassadbdetection/SecureShim;"
private const val SYSTEM_SHIM = "Lio/github/bholeykabhakt/extension/bypassadbdetection/SystemShim;"

// Skip all extension code so we don't rewrite invokes inside the shim itself
// (which would loop) or inside the AGP-generated R class / future helpers.
private const val EXTENSION_PACKAGE_PREFIX = "Lio/github/bholeykabhakt/extension/"

private val SHIM_FOR = mapOf(
    GLOBAL_DESC to GLOBAL_SHIM,
    SECURE_DESC to SECURE_SHIM,
    SYSTEM_DESC to SYSTEM_SHIM,
)

private val TARGET_METHOD_NAMES = setOf(
    "getInt",
    "getString",
    "getLong",
    "getFloat",
    "getUriFor",
)

/**
 * Bypasses ADB / Developer-Options detection at the Settings.getXxx() API surface.
 *
 * Merges a shim DEX that mirrors Settings.{Global,Secure,System} static getters, then
 * rewrites every invoke-static of those getters in the app to point at the shim. At
 * runtime the shim returns "off" for `adb_enabled`, `adb_wifi_enabled`,
 * `development_settings_enabled`; everything else passes through unchanged.
 *
 * This catches helper indirection and most key-obfuscation tactics because the
 * decision happens on the runtime String, not the bytecode literal.
 *
 * Not covered: ContentResolver.query against settings URIs, SystemProperties reads,
 * Debug.isDebuggerConnected, ApplicationInfo.FLAG_DEBUGGABLE.
 */
@Suppress("unused")
val bypassAdbDetectionPatch = bytecodePatch(
    name = "Bypass ADB/Developer Options Detection",
    default = false,
) {
    extendWith("extensions/bypassadbdetection.mpe")

    execute {
        classDefForEach { classDef ->
            if (classDef.type.startsWith(EXTENSION_PACKAGE_PREFIX)) return@classDefForEach

            val mutableClass = mutableClassDefBy(classDef.type)
            mutableClass.methods.forEach methodLoop@{ method ->
                val impl = method.implementation ?: return@methodLoop

                // replaceInstruction swaps in place — size stable, indices stay valid.
                val count = impl.instructions.size
                for (idx in 0 until count) {
                    val insn = impl.getInstruction(idx)
                    if (insn !is ReferenceInstruction) continue
                    if (insn.opcode != Opcode.INVOKE_STATIC && insn.opcode != Opcode.INVOKE_STATIC_RANGE) continue

                    val ref = insn.reference as? MethodReference ?: continue
                    val shim = SHIM_FOR[ref.definingClass] ?: continue
                    if (ref.name !in TARGET_METHOD_NAMES) continue

                    val newReference = ImmutableMethodReference(
                        shim,
                        ref.name,
                        ref.parameterTypes,
                        ref.returnType,
                    )

                    val newInsn = when (insn) {
                        is BuilderInstruction35c -> BuilderInstruction35c(
                            insn.opcode,
                            insn.registerCount,
                            insn.registerC,
                            insn.registerD,
                            insn.registerE,
                            insn.registerF,
                            insn.registerG,
                            newReference,
                        )

                        is BuilderInstruction3rc -> BuilderInstruction3rc(
                            insn.opcode,
                            insn.startRegister,
                            insn.registerCount,
                            newReference,
                        )

                        else -> continue
                    }
                    method.replaceInstruction(idx, newInsn)
                }
            }
        }
    }
}
