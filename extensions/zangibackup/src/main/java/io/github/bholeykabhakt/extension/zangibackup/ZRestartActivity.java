package io.github.bholeykabhakt.extension.zangibackup;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import java.io.File;

/**
 * Runs in its own {@code :zbrestart} process (declared by the patch): waits for the old main process
 * to die, relaunches Zangi (whose attachBaseContext then swaps the staged restore in) and exits.
 * The patch also makes MainApplication.onCreate skip Zangi's init in this process, so nothing here
 * opens Zangi's databases.
 */
public final class ZRestartActivity extends Activity {
    public static final String EXTRA_PID = "zb_pid";
    public static final String PROCESS = ":zbrestart";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        final int pid = getIntent().getIntExtra(EXTRA_PID, -1);
        final Handler h = new Handler(Looper.getMainLooper());
        final long deadline = System.currentTimeMillis() + 5000;
        h.post(new Runnable() {
            @Override public void run() {
                if (pid > 0 && new File("/proc/" + pid).exists() && System.currentTimeMillis() < deadline) {
                    h.postDelayed(this, 100);
                    return;
                }
                Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(launch);
                }
                finish();
                h.postDelayed(() -> Process.killProcess(Process.myPid()), 300);
            }
        });
    }
}
