package io.github.bholeykabhakt.patches.zangi

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.document
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.Opcode
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_ZANGI
import io.github.bholeykabhakt.patches.utils.applicationElement
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.requireFreeRegisters

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
        val appOnCreate = ApplicationOnCreateFingerprint.logMatch.method
        appOnCreate.requireFreeRegisters(1)
        appOnCreate.addInstructionsWithLabels(
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
        // At the branch label of each return, so early-exit branches that jump to it run the hook too.
        returns.asReversed().forEach {
            onCreate.addInstructionsAtControlFlowLabel(it, "invoke-static { p0 }, $HOOKS->mainCreated(Landroid/app/Activity;)V")
        }

        MainActivityOnStopFingerprint.logMatch.method.addInstruction(
            0,
            "invoke-static { p0 }, $HOOKS->mainStopped(Landroid/app/Activity;)V",
        )

        val transfer = OpenTransferDialogFingerprint.logMatch.method
        transfer.requireFreeRegisters(1)
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
            val app = doc.applicationElement()
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
                // Keep the running job's screen across rotation, resizing and dark mode instead of recreating it.
                "android:configChanges" to "orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|uiMode",
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
