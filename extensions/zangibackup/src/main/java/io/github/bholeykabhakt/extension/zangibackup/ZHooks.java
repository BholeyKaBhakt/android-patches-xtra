package io.github.bholeykabhakt.extension.zangibackup;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.lang.reflect.Method;

/** Entry points called from patched Zangi bytecode. Every hook swallows its own errors. */
public final class ZHooks {
    private static boolean transferBypass;

    private ZHooks() {}

    /** MainApplication.attachBaseContext(Context), first instruction. */
    public static void attachBaseContext(Context base) {
        ZBackup.applyPendingRestore(base);
    }

    /** MainApplication.onCreate(), first instruction: true = this is the restart trampoline process. */
    public static boolean isRestartProcess() {
        String name = ZBackup.processName();
        return name != null && name.endsWith(ZRestartActivity.PROCESS);
    }

    /** MainActivity.onCreate(Bundle), before return: offer a restore once on a fresh install. */
    public static void mainCreated(final Activity a) {
        try {
            if (ZBackup.isLoggedIn(a) || ZBackup.prefs(a).getBoolean("restore_offered", false)) return;
            ZBackup.prefs(a).edit().putBoolean("restore_offered", true).apply();
            a.getWindow().getDecorView().post(() -> {
                if (a.isFinishing() || a.isDestroyed()) return;
                new AlertDialog.Builder(a)
                        .setTitle("Restore a Zangi backup?")
                        .setMessage("If you backed up Zangi with this build before, you can restore your account, "
                                + "chats and media now instead of registering again.")
                        .setPositiveButton("Restore", (d, w) -> a.startActivity(
                                new Intent(a, ZBackupActivity.class).putExtra(ZBackupActivity.EXTRA_RESTORE, true)))
                        .setNegativeButton("Not now", null)
                        .show();
            });
        } catch (Throwable t) {
            Log.e(ZBackup.TAG, "mainCreated", t);
        }
    }

    /** MainActivity.onStop(), first instruction. */
    public static void mainStopped(Activity a) {
        try {
            ZBackup.autoBackup(a);
        } catch (Throwable t) {
            Log.e(ZBackup.TAG, "mainStopped", t);
        }
    }

    /**
     * First instruction of the settings "Transfer to new device" handler. Returns true when it took
     * over (showed the chooser); the Wi-Fi choice re-enters {@code method} with the bypass set.
     */
    public static boolean interceptTransfer(final Object host, final String method) {
        if (transferBypass) {
            transferBypass = false;
            return false;
        }
        try {
            final Activity a = activityOf(host);
            if (a == null) return false;
            new AlertDialog.Builder(a)
                    .setTitle("Transfer or back up")
                    .setItems(new CharSequence[]{"Transfer to a new device (Wi-Fi)", "Backup & restore"}, (d, which) -> {
                        if (which == 1) {
                            a.startActivity(new Intent(a, ZBackupActivity.class));
                            return;
                        }
                        try {
                            Method m = host.getClass().getDeclaredMethod(method);
                            m.setAccessible(true);
                            transferBypass = true;
                            m.invoke(host);
                        } catch (Throwable t) {
                            transferBypass = false;
                            Log.e(ZBackup.TAG, "transfer re-entry", t);
                        }
                    })
                    .show();
            return true;
        } catch (Throwable t) {
            Log.e(ZBackup.TAG, "interceptTransfer", t);
            return false;
        }
    }

    static Activity activityOf(Object host) {
        if (host instanceof Activity) return (Activity) host;
        try {
            Object a = host.getClass().getMethod("getActivity").invoke(host);   // androidx Fragment
            return a instanceof Activity ? (Activity) a : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
