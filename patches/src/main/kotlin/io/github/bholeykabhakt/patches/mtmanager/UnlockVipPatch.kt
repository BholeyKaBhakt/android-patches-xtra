package io.github.bholeykabhakt.patches.mtmanager

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.deleteResource
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceFile
import app.morphe.patcher.patch.retainOriginalV2Signature
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_MT_MANAGER
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.method

// Obfuscated classes: l.\u1a7b\u1a76\u06eb = Application superclass (its <clinit> loads libmtprotect);
// l.\u06e8\u06e1 / l.\u073d\u06ec\u06eb = the two android.app.Dialog subclasses;
// l.\u06e4\u1a77\u06e2 = toolbar subtitle (text or string id).
private const val APP_SUPER = "Ll/\u1a7b\u1a76\u06eb;"
private const val DIALOG_A = "Ll/\u06e8\u06e1;"
private const val DIALOG_B = "Ll/\u073d\u06ec\u06eb;"
private const val MAIN_ACTIVITY = "Lbin/mt/plus/Main;"
private const val SUBTITLE = "Ll/\u06e4\u1a77\u06e2;"
private const val SUBTITLE_TEXT = "\u05a8"
private const val SUBTITLE_RES = "\u05ab"

private const val LIBMTHOOK_PATH = "lib/arm64-v8a/libmthook.so"

private object Bundled

@Suppress("unused")
val unlockVipPatch = patch(
    name = "Unlock VIP",
    description = "Some features are server-side locked",
) {
    compatibleWith(COMPATIBILITY_MT_MANAGER)

    execute {
        retainOriginalV2Signature()

        val lib = Bundled::class.java.getResourceAsStream("/mtmanager/libmthook.so")!!.use { it.readBytes() }
        resourceFile(LIBMTHOOK_PATH, true).apply { parentFile?.mkdirs() }.writeBytes(lib)
        // libmthook is arm64-only: drop the other ABIs so the APK only installs where it loads.
        for (abi in listOf("armeabi-v7a", "x86", "x86_64")) deleteResource("lib/$abi/")

        // Load libmthook ahead of libmtprotect (same <clinit>) so its signature swap precedes the cert check.
        mutableClassDefBy(APP_SUPER).method("<clinit>").addInstructions(
            0,
            """
                const-string v0, "mthook"
                invoke-static { v0 }, Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V
            """.trimIndent(),
        )

        // Bridge MT's dialog lifecycle to libmthook (native dlg(int)), so VIP is forced only around a dialog.
        mutableClassDefBy(APP_SUPER).methods.add(
            ImmutableMethod(
                APP_SUPER, "dlg", listOf(ImmutableMethodParameter("I", null, null)), "V",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.NATIVE.value,
                null, null, null,
            ).toMutable(),
        )
        fun bridge(classType: String, method: String, event: Int) =
            mutableClassDefBy(classType).method(method).addInstructions(
                0, "const/4 v0, ${if (event < 0) "-0x1" else "0x$event"}\ninvoke-static { v0 }, $APP_SUPER->dlg(I)V",
            )
        bridge(DIALOG_A, "onStart", 1)
        bridge(DIALOG_A, "onStop", -1)
        bridge(DIALOG_B, "onStart", 1)
        bridge(DIALOG_B, "dismiss", -1)
        bridge(MAIN_ACTIVITY, "onResume", 0)

        // The network-storage protocol chooser is a SubMenu (not a Dialog): take its unlocked branch.
        NetStorageChooserFingerprint.logMatch.let { match ->
            match.method.addInstructions(match.instructionMatches.first().index + 2, "const/4 v0, 0x1")
        }

        // While VIP is forced, the account-status subtitle can carry a string id of -1: show its text instead.
        mutableClassDefBy(SUBTITLE).methods.first {
            it.name == SUBTITLE_RES && it.returnType == "Ljava/lang/String;" && it.parameterTypes.isEmpty()
        }.addInstructionsWithLabels(
            0,
            """
                iget v0, p0, $SUBTITLE->$SUBTITLE_RES:I
                if-gez v0, :mt_res
                iget-object v0, p0, $SUBTITLE->$SUBTITLE_TEXT:Ljava/lang/String;
                return-object v0
                :mt_res
                nop
            """,
        )
    }
}
