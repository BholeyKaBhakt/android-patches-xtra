package io.github.bholeykabhakt.patches.zangi

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.document
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_ZANGI
import io.github.bholeykabhakt.patches.utils.logMatch
import org.w3c.dom.Element

private const val EXT = "io.github.bholeykabhakt.extension.zangibackup"
private const val HOOKS = "Lio/github/bholeykabhakt/extension/zangibackup/ZHooks;"

/**
 * Password-encrypted backup/restore of the account, chats, E2E identity and media (Zangi itself only
 * has a live Wi-Fi transfer). Entry: Settings › Transfer to new device › "Backup & restore"; a fresh
 * install offers a restore once. Restore is staged and swapped in at the next process start, before
 * any database is opened.
 */
@Suppress("unused")
val backupRestorePatch = patch(
    name = "Backup & Restore",
) {
    compatibleWith(COMPATIBILITY_ZANGI)

    extendWith("extensions/zangibackup.mpe")

    execute {
        AttachBaseContextFingerprint.logMatch.method.addInstruction(
            0,
            "invoke-static { p1 }, $HOOKS->attachBaseContext(Landroid/content/Context;)V",
        )

        // The restart trampoline process must not run Zangi's init (it would open the DBs mid-swap).
        ApplicationOnCreateFingerprint.logMatch.method.addInstructionsWithLabels(
            0,
            """
                invoke-static { }, $HOOKS->isRestartProcess()Z
                move-result v0
                if-eqz v0, :zb_init
                invoke-super { p0 }, Landroid/app/Application;->onCreate()V
                return-void
                :zb_init
                nop
            """,
        )

        val onCreate = MainActivityOnCreateFingerprint.logMatch.method
        val returns = onCreate.implementation!!.instructions.withIndex()
            .filter { it.value.opcode == Opcode.RETURN_VOID }.map { it.index }
        if (returns.isEmpty()) throw PatchException("MainActivity.onCreate has no return-void")
        returns.asReversed().forEach {
            onCreate.addInstruction(it, "invoke-static { p0 }, $HOOKS->mainCreated(Landroid/app/Activity;)V")
        }

        MainActivityOnStopFingerprint.logMatch.method.addInstruction(
            0,
            "invoke-static { p0 }, $HOOKS->mainStopped(Landroid/app/Activity;)V",
        )

        val transfer = OpenTransferDialogFingerprint.logMatch.method
        transfer.addInstructionsWithLabels(
            0,
            """
                const-string v0, "${transfer.name}"
                invoke-static { p0, v0 }, $HOOKS->interceptTransfer(Ljava/lang/Object;Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :zb_go
                return-void
                :zb_go
                nop
            """,
        )

        document("AndroidManifest.xml").use { doc ->
            val app = doc.getElementsByTagName("application").item(0) as Element
            fun activity(name: String, vararg attrs: Pair<String, String>) {
                val a = doc.createElement("activity")
                a.setAttribute("android:name", "$EXT.$name")
                a.setAttribute("android:exported", "false")
                attrs.forEach { (k, v) -> a.setAttribute(k, v) }
                app.appendChild(a)
            }
            activity(
                "ZBackupActivity",
                "android:theme" to "@android:style/Theme.DeviceDefault.DayNight",
                "android:label" to "Backup & restore",
            )
            activity(
                "ZRestartActivity",
                "android:process" to ":zbrestart",
                "android:theme" to "@android:style/Theme.Translucent.NoTitleBar",
                "android:excludeFromRecents" to "true",
                "android:noHistory" to "true",
            )
        }
    }
}
