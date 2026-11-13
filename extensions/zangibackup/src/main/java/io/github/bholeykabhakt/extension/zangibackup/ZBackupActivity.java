package io.github.bholeykabhakt.extension.zangibackup;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Backup & restore screen (plain framework widgets, so it needs no resources in the host APK). */
public final class ZBackupActivity extends Activity {
    public static final String EXTRA_RESTORE = "zb_restore";

    static final int REQ_FOLDER = 1, REQ_EXPORT = 2, REQ_RESTORE_FILE = 3, REQ_RESTORE_FOLDER = 4;

    private TextView info, status;
    private ProgressBar progress;
    private CheckBox auto;
    private Button passwordBtn;
    private final List<View> actions = new ArrayList<>();
    private boolean exportWithMedia;
    private volatile boolean busy;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Backup & restore");
        if (Build.VERSION.SDK_INT < 26) {
            TextView t = new TextView(this);
            t.setText("Backup needs Android 8.0 or newer.");
            t.setPadding(dp(16), dp(16), dp(16), dp(16));
            setContentView(t);
            return;
        }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(8), dp(16), dp(24));

        info = text(col, "", 14);
        section(col, "Password");
        text(col, "Every backup is encrypted with this password. It holds your account key: without the "
                + "password a backup can't be restored, and nobody can use it.", 13);
        passwordBtn = button(col, "", v -> askNewPassword(null));

        section(col, "Automatic backup");
        auto = new CheckBox(this);
        auto.setText("Back up when leaving Zangi (if anything changed, at most every 15 min)");
        auto.setOnClickListener(v -> {
            if (auto.isChecked() && (ZBackup.folder(this) == null || !ZBackup.hasPassword(this))) {
                auto.setChecked(false);
                pickFolder();
                return;
            }
            ZBackup.setAuto(this, auto.isChecked());
            refresh();
        });
        col.addView(auto);
        actions.add(auto);
        button(col, "Choose backup folder", v -> pickFolder());
        button(col, "Back up now", v -> withPassword(this::backupNow));

        section(col, "Export");
        text(col, "One encrypted file you can keep anywhere (Drive, USB, another phone).", 13);
        button(col, "Export to a file…", v -> withPassword(this::askExport));

        section(col, "Restore");
        text(col, "Replaces this app's account, chats and settings with the backup, then restarts Zangi.", 13);
        button(col, "Restore from backup folder…", v -> startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_RESTORE_FOLDER));
        button(col, "Restore from a file…", v -> startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),
                REQ_RESTORE_FILE));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setVisibility(View.GONE);
        col.addView(progress);
        status = text(col, "", 13);

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);
        refresh();

        if (state == null && getIntent().getBooleanExtra(EXTRA_RESTORE, false)) {
            new AlertDialog.Builder(this)
                    .setTitle("Restore from")
                    .setItems(new CharSequence[]{"A backup folder", "A backup file"}, (d, w) -> startActivityForResult(
                            w == 0 ? new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                    : new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),
                            w == 0 ? REQ_RESTORE_FOLDER : REQ_RESTORE_FILE))
                    .show();
        }
    }

    // ---- state ------------------------------------------------------------------------------

    private void refresh() {
        SharedPreferences p = ZBackup.prefs(this);
        StringBuilder sb = new StringBuilder();
        String acc = ZBackup.accountNumber(this);
        sb.append("Account: ").append(acc.isEmpty() ? "not signed in" : acc).append('\n');
        Uri f = ZBackup.folder(this);
        sb.append("Backup folder: ").append(f == null ? "not chosen" : Uri.decode(f.getLastPathSegment())).append('\n');
        long last = p.getLong("last_backup", 0);
        sb.append("Last backup: ").append(last == 0 ? "never" : when(last) + " (" + Formatter.formatShortFileSize(this, p.getLong("last_size", 0)) + ")");
        String err = p.getString("last_error", "");
        if (!err.isEmpty()) sb.append("\nLast automatic backup failed: ").append(err);
        info.setText(sb);
        passwordBtn.setText(ZBackup.hasPassword(this) ? "Change password" : "Set password");
        auto.setChecked(ZBackup.autoEnabled(this) && f != null && ZBackup.hasPassword(this));
    }

    private void setBusy(boolean b, String msg) {
        busy = b;
        for (View v : actions) v.setEnabled(!b);
        passwordBtn.setEnabled(!b);
        progress.setVisibility(b ? View.VISIBLE : View.GONE);
        progress.setIndeterminate(true);
        status.setText(msg == null ? "" : msg);
    }

    private ZBackup.Progress progressSink() {
        return (stage, done, total) -> runOnUiThread(() -> {
            if (!busy) return;
            status.setText(stage);
            if (total > 0) {
                progress.setIndeterminate(false);
                progress.setMax((int) Math.min(total, Integer.MAX_VALUE));
                progress.setProgress((int) Math.min(done, Integer.MAX_VALUE));
            } else {
                progress.setIndeterminate(true);
            }
        });
    }

    interface Job { String run() throws Exception; }

    private void runJob(String start, Job job) {
        setBusy(true, start);
        new Thread(() -> {
            String msg;
            boolean ok;
            try {
                msg = job.run();
                ok = true;
            } catch (Throwable t) {
                msg = t.getMessage() == null ? t.toString() : t.getMessage();
                ok = false;
            }
            final String m = msg;
            final boolean success = ok;
            runOnUiThread(() -> {
                setBusy(false, null);
                refresh();
                if (m != null) new AlertDialog.Builder(this)
                        .setTitle(success ? "Done" : "Failed").setMessage(m).setPositiveButton("OK", null).show();
            });
        }, "ZBackup-ui").start();
    }

    // ---- password ---------------------------------------------------------------------------

    private void withPassword(Runnable next) {
        if (ZBackup.hasPassword(this)) next.run();
        else askNewPassword(next);
    }

    private void askNewPassword(Runnable next) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        EditText a = passwordField(box, "New password (6+ characters)");
        EditText b = passwordField(box, "Repeat password");
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(ZBackup.hasPassword(this) ? "Change backup password" : "Set backup password")
                .setMessage(ZBackup.hasPassword(this) ? "New backups use the new password; older backup files keep the old one." : null)
                .setView(box)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String pw = a.getText().toString();
            if (pw.length() < 6) { a.setError("At least 6 characters"); return; }
            if (!pw.equals(b.getText().toString())) { b.setError("Doesn't match"); return; }
            ZBackup.setPassword(this, pw);
            d.dismiss();
            refresh();
            if (next != null) next.run();
        });
    }

    interface PasswordCallback { void got(char[] pw); }

    private void askPassword(String title, PasswordCallback cb) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        EditText a = passwordField(box, "Backup password");
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("OK", (d, w) -> cb.got(a.getText().toString().toCharArray()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- actions ----------------------------------------------------------------------------

    private void pickFolder() {
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION), REQ_FOLDER);
    }

    private void backupNow() {
        if (ZBackup.folder(this) == null) { pickFolder(); return; }
        runJob("Backing up…", () -> "Saved " + ZBackup.backupToFolder(this, progressSink()));
    }

    private void askExport() {
        new AlertDialog.Builder(this)
                .setTitle("Export")
                .setItems(new CharSequence[]{"Account and chats (small)", "Account, chats and all media (large)"}, (d, w) -> {
                    exportWithMedia = w == 1;
                    startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE).setType(ZBackup.MIME)
                            .putExtra(Intent.EXTRA_TITLE, "zangi-" + ZBackup.stamp() + ZBackup.EXT), REQ_EXPORT);
                })
                .show();
    }

    private void restore(Uri uri, boolean folder) {
        askPassword("Backup password", pw -> runJob("Checking the backup…", () -> {
            Uri archive = folder ? ZBackup.newestArchive(this, uri) : uri;
            if (archive == null) throw new Exception("No Zangi backup in that folder.");
            JSONObject meta = ZBackup.readMeta(this, archive, pw);
            String problem = ZBackup.checkCompatible(this, meta);
            if (problem != null) throw new Exception(problem);
            runOnUiThread(() -> confirmRestore(uri, folder, pw, meta));
            return null;
        }));
    }

    private void confirmRestore(Uri uri, boolean folder, char[] pw, JSONObject meta) {
        String acc = meta.optString("account");
        String current = ZBackup.accountNumber(this);
        StringBuilder msg = new StringBuilder()
                .append("Account: ").append(acc).append('\n')
                .append("Made: ").append(when(meta.optLong("created"))).append('\n')
                .append("On: ").append(meta.optString("device")).append(", Zangi ").append(meta.optString("versionName")).append('\n')
                .append(folder ? "Media: from the folder's media mirror" : meta.optBoolean("media") ? "Media: included" : "Media: not included")
                .append("\n\n");
        if (!current.isEmpty() && !current.equals(acc))
            msg.append("This replaces the signed-in account ").append(current).append(" and its chats on this phone.\n\n");
        else if (!current.isEmpty())
            msg.append("Messages received since this backup was made are replaced by the backup's state.\n\n");
        msg.append("Don't keep this account running on another phone at the same time.");
        new AlertDialog.Builder(this)
                .setTitle("Restore this backup?")
                .setMessage(msg)
                .setPositiveButton("Restore", (d, w) -> runJob("Restoring…", () -> {
                    JSONObject m = folder ? ZBackup.stageFromFolder(this, uri, pw, progressSink())
                            : ZBackup.stageArchive(this, uri, pw, progressSink());
                    ZBackup.adoptRestoreSource(this, folder ? uri : null, pw);
                    int failed = m.optInt("media_failed", 0);
                    runOnUiThread(() -> new AlertDialog.Builder(this)
                            .setTitle("Restore ready")
                            .setMessage("Zangi restarts now to finish." + (failed > 0 ? "\n(" + failed + " media files couldn't be restored.)" : ""))
                            .setCancelable(false)
                            .setPositiveButton("Restart", (d2, w2) -> ZBackup.restartApp(this))
                            .show());
                    return null;
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        switch (req) {
            case REQ_FOLDER:
                try {
                    ZBackup.setFolder(this, uri);
                } catch (SecurityException e) {
                    new AlertDialog.Builder(this).setMessage("That folder can't be kept; pick another.").show();
                    return;
                }
                withPassword(() -> {
                    ZBackup.setAuto(this, true);
                    refresh();
                    backupNow();
                });
                break;
            case REQ_EXPORT:
                final boolean media = exportWithMedia;
                runJob("Exporting…", () -> {
                    ZBackup.exportToUri(this, uri, media, progressSink());
                    return "Exported. Keep the password: the file can't be restored without it.";
                });
                break;
            case REQ_RESTORE_FILE:
                restore(uri, false);
                break;
            case REQ_RESTORE_FOLDER:
                restore(uri, true);
                break;
        }
    }

    // ---- small view helpers -----------------------------------------------------------------

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView text(LinearLayout parent, String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setPadding(0, dp(4), 0, dp(4));
        parent.addView(t);
        return t;
    }

    private void section(LinearLayout parent, String s) {
        TextView t = text(parent, s, 16);
        t.setPadding(0, dp(20), 0, dp(4));
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
    }

    private Button button(LinearLayout parent, String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        parent.addView(b, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        actions.add(b);
        return b;
    }

    private EditText passwordField(LinearLayout parent, String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        parent.addView(e);
        return e;
    }

    private String when(long t) {
        return DateFormat.getMediumDateFormat(this).format(new Date(t)) + " " + DateFormat.getTimeFormat(this).format(new Date(t));
    }
}
