package io.github.bholeykabhakt.extension.common;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Streams a .zip from a URL or a picked document straight into a staging dir (no temp archive,
 * so the disk peak is the extracted size), then swaps it into place. Entries are re-rooted at
 * the first path segment named in {@code roots}, so a zip with an extra top folder (e.g. a Play
 * dump's {@code assets/}) installs the same; anything outside it is skipped. SHA-256 is computed
 * while streaming and checked before the swap: the given one, else a {@code <url>.sha256} published
 * next to the download (skipped when that file does not exist). Work runs on a background thread behind
 * a cancellable progress dialog; the {@link Callback} fires on the UI thread.
 */
public final class ZipInstaller {

    public interface Callback {
        void onSuccess();

        /** {@code error} is null when the user cancelled. */
        void onFailure(String error);
    }

    /** Checks the staged tree before it replaces the current one; return an error or null. */
    public interface Validator {
        String check(File staged);
    }

    /**
     * Optional one-line label at the archive root (e.g. the pack version). Shown in the dialog as
     * soon as it is unpacked, so pack it first; {@link #label(File)} reads it back after install.
     */
    public static final String LABEL_FILE = "VERSION";

    /** Dark device-default dialogs, shared with the caller's own dialogs so they all match. */
    public static final int THEME = android.R.style.Theme_DeviceDefault_Dialog_Alert;

    private static final class Cancelled extends IOException {
    }

    private ZipInstaller() {
    }

    public static void fromUrl(Activity activity, String url, String sha256, File dest, String[] roots,
                               Validator validator, String title, Callback cb) {
        start(activity, null, url, sha256, dest, roots, validator, title, cb);
    }

    public static void fromUri(Activity activity, Uri uri, File dest, String[] roots,
                               Validator validator, String title, Callback cb) {
        start(activity, uri, null, null, dest, roots, validator, title, cb);
    }

    private static void start(final Activity activity, final Uri uri, final String url, final String sha256,
                              final File dest, final String[] roots, final Validator validator,
                              final String title, final Callback cb) {
        final Ui ui = new Ui(activity, title, uri == null ? "Connecting…" : "Opening file…");
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        new Thread(new Runnable() {
            @Override
            public void run() {
                File stage = new File(dest.getParentFile(), dest.getName() + ".tmp");
                File old = new File(dest.getParentFile(), dest.getName() + ".old");
                HttpURLConnection conn = null;
                try {
                    deleteRecursive(stage);
                    deleteRecursive(old);
                    InputStream raw;
                    long total;
                    if (uri != null) {
                        raw = activity.getContentResolver().openInputStream(uri);
                        if (raw == null) throw new IOException("cannot open the file");
                        total = sizeOf(activity, uri);
                    } else {
                        conn = open(url, ui);
                        raw = conn.getInputStream();
                        total = conn.getContentLengthLong();
                    }
                    ui.base = uri == null ? "Downloading and unpacking" : "Unpacking";
                    ui.phase(ui.base);
                    String expected = sha256;
                    if (uri == null && !wantsSha(expected)) expected = sidecarSha(url + ".sha256");
                    MessageDigest md = wantsSha(expected) ? MessageDigest.getInstance("SHA-256") : null;
                    InputStream in = new Progress(new BufferedInputStream(raw, 1 << 16), total, ui);
                    if (md != null) in = new DigestInputStream(in, md);
                    try {
                        extract(in, stage, roots, ui);
                        drain(in); // central directory, so the digest covers the whole file
                    } finally {
                        in.close();
                    }
                    ui.phase("Checking files");
                    ui.finishing();
                    if (md != null && !hex(md.digest()).equalsIgnoreCase(expected.trim())) {
                        throw new IOException("checksum mismatch, the file is damaged or not the expected one");
                    }
                    String bad = validator == null ? null : validator.check(stage);
                    if (bad != null) throw new IOException(bad);
                    ui.check();

                    if (dest.exists() && !dest.renameTo(old)) throw new IOException("cannot replace the current files");
                    if (!stage.renameTo(dest)) {
                        old.renameTo(dest);
                        throw new IOException("cannot move the files into place");
                    }
                    deleteRecursive(old);
                    finish(activity, ui, cb, null, false);
                } catch (Exception e) {
                    deleteRecursive(stage);
                    boolean cancelled = e instanceof Cancelled || ui.cancelled;
                    String msg = e.getMessage();
                    finish(activity, ui, cb, msg == null ? e.getClass().getSimpleName() : msg, cancelled);
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }, "zip-install").start();
    }

    /**
     * The hash from a {@code sha256sum}-style file ({@code <hex>  <name>}), or null when it is
     * absent (HTTP 404). Any other failure aborts, so a download is never silently unchecked.
     */
    private static String sidecarSha(String url) throws IOException {
        HttpURLConnection c;
        try {
            c = open(url, null);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("404")) return null;
            throw new IOException("cannot read the checksum file: " + e.getMessage());
        }
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream()))) {
            String line = r.readLine();
            String hex = line == null ? "" : line.trim().split("\\s+")[0];
            if (!hex.matches("[0-9a-fA-F]{64}")) throw new IOException("the checksum file is malformed");
            return hex;
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String url, Ui ui) throws IOException {
        String current = url;
        for (int redirects = 0; redirects < 6; redirects++) {
            if (ui != null) ui.check();
            HttpURLConnection c = (HttpURLConnection) new URL(current).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(30000);
            c.setReadTimeout(60000);
            if (ui != null) ui.conn = c;
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("redirect without Location");
                current = new URL(new URL(current), loc).toString();
                continue;
            }
            if (code != 200) {
                c.disconnect();
                throw new IOException(code == 404 ? "the file is not on the server (HTTP 404)" : "server replied HTTP " + code);
            }
            return c;
        }
        throw new IOException("too many redirects");
    }

    private static void extract(InputStream in, File stage, String[] roots, Ui ui) throws IOException {
        if (!stage.mkdirs()) throw new IOException("cannot create " + stage.getName());
        String canon = stage.getCanonicalPath() + File.separator;
        ZipInputStream zin = new ZipInputStream(in);
        byte[] buf = new byte[1 << 16];
        String prefix = null;
        int files = 0;
        ZipEntry e;
        while ((e = zin.getNextEntry()) != null) {
            String name = e.getName().replace('\\', '/');
            if (prefix == null) prefix = prefixOf(name, roots);
            if (prefix == null || !name.startsWith(prefix) || name.length() == prefix.length()) continue;
            String rel = name.substring(prefix.length());
            File out = new File(stage, rel);
            if (!out.getCanonicalPath().startsWith(canon)) throw new IOException("bad entry " + name);
            if (e.isDirectory()) {
                out.mkdirs();
                continue;
            }
            File parent = out.getParentFile();
            if (parent != null) parent.mkdirs();
            OutputStream fo = new FileOutputStream(out);
            try {
                int n;
                while ((n = zin.read(buf)) != -1) fo.write(buf, 0, n);
            } finally {
                fo.close();
            }
            ui.files = ++files;
            if (rel.equals(LABEL_FILE)) {
                String v = label(stage);
                if (v != null) ui.phase(ui.base + " · " + v);
            }
        }
        if (files == 0) throw new IOException("not a catalog archive");
    }

    /** Path up to the first segment named in {@code roots}, or null if this entry has none. */
    private static String prefixOf(String name, String[] roots) {
        String[] parts = name.split("/");
        StringBuilder prefix = new StringBuilder();
        for (String part : parts) {
            for (String root : roots) if (part.equals(root)) return prefix.toString();
            prefix.append(part).append('/');
        }
        return null;
    }

    private static void drain(InputStream in) throws IOException {
        byte[] buf = new byte[1 << 16];
        while (in.read(buf) != -1) {
            // consume
        }
    }

    private static long sizeOf(Activity activity, Uri uri) {
        try (Cursor c = activity.getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static boolean wantsSha(String sha) {
        return sha != null && !sha.trim().isEmpty();
    }

    private static String hex(byte[] digest) {
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format(Locale.US, "%02x", b));
        return sb.toString();
    }

    public static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        f.delete();
    }

    /** First line of {@link #LABEL_FILE} in {@code dir}, or null. */
    public static String label(File dir) {
        File f = new File(dir, LABEL_FILE);
        if (!f.isFile() || f.length() > 4096) return null;
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f))) {
            String line = r.readLine();
            return line == null || line.trim().isEmpty() ? null : line.trim();
        } catch (IOException e) {
            return null;
        }
    }

    /** Total size of the files under {@code f}. */
    public static long sizeOnDisk(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long sum = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) sum += sizeOnDisk(k);
        return sum;
    }

    /** Decimal units, like download hosts report sizes. */
    public static String human(long bytes) {
        if (bytes < 0) return "?";
        double mb = bytes / 1_000_000.0;
        if (mb >= 1000.0) return String.format(Locale.US, "%.2f GB", mb / 1000.0);
        if (mb >= 100.0) return String.format(Locale.US, "%.0f MB", mb);
        return String.format(Locale.US, "%.1f MB", mb);
    }

    private static String eta(long seconds) {
        if (seconds < 60) return "under a minute left";
        long min = (seconds + 30) / 60;
        if (min < 60) return min + " min left";
        return String.format(Locale.US, "%d h %02d min left", min / 60, min % 60);
    }

    public static int dp(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    private static void finish(final Activity activity, final Ui ui, final Callback cb, final String err,
                               final boolean cancelled) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                ui.dismiss();
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (cancelled) cb.onFailure(null);
                else if (err == null) cb.onSuccess();
                else cb.onFailure(err);
            }
        });
    }

    /** Progress dialog: phase line, bar, "x of y · speed · eta" line and a Cancel button. */
    private static final class Ui {
        final Activity activity;
        final AlertDialog dialog;
        final TextView phase;
        final ProgressBar bar;
        final TextView detail;
        volatile boolean cancelled;
        volatile HttpURLConnection conn;
        volatile int files;
        volatile String base = "";

        Ui(Activity activity, String title, String firstPhase) {
            this.activity = activity;
            Context ctx = new android.view.ContextThemeWrapper(activity, THEME);
            LinearLayout box = new LinearLayout(ctx);
            box.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(ctx, 24);
            box.setPadding(pad, dp(ctx, 8), pad, dp(ctx, 4));

            phase = new TextView(ctx);
            phase.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium);
            phase.setTextSize(15);
            phase.setText(firstPhase);
            box.addView(phase);

            bar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
            bar.setIndeterminate(true);
            bar.setMax(1000);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(ctx, 12);
            lp.bottomMargin = dp(ctx, 6);
            box.addView(bar, lp);

            detail = new TextView(ctx);
            detail.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
            detail.setAlpha(0.7f);
            detail.setText("Keep the app open until this finishes.");
            box.addView(detail);

            dialog = new AlertDialog.Builder(activity, THEME)
                    .setTitle(title)
                    .setView(box)
                    .setCancelable(false)
                    .setNegativeButton("Cancel", null)
                    .create();
            dialog.show();
            final Button cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cancelled = true;
                    cancel.setEnabled(false);
                    cancel.setText("Cancelling…");
                    final HttpURLConnection c = conn;
                    if (c != null) {
                        // Unblocks a stalled read; off the UI thread since it touches the socket.
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                c.disconnect();
                            }
                        }, "zip-cancel").start();
                    }
                }
            });
        }

        void check() throws Cancelled {
            if (cancelled) throw new Cancelled();
        }

        void phase(final String text) {
            ui(new Runnable() {
                @Override
                public void run() {
                    phase.setText(text);
                }
            });
        }

        /** Past the point of no return: the swap is quick, so Cancel goes away. */
        void finishing() {
            ui(new Runnable() {
                @Override
                public void run() {
                    bar.setIndeterminate(true);
                    detail.setText(files + " files");
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setVisibility(View.GONE);
                }
            });
        }

        void progress(final long done, final long total, final double bytesPerSec) {
            ui(new Runnable() {
                @Override
                public void run() {
                    StringBuilder s = new StringBuilder();
                    if (total > 0) {
                        bar.setIndeterminate(false);
                        bar.setProgress((int) Math.min(1000, done * 1000 / total));
                        s.append(human(done)).append(" of ").append(human(total));
                    } else {
                        s.append(human(done));
                    }
                    if (bytesPerSec > 0) {
                        s.append(" · ").append(human((long) bytesPerSec)).append("/s");
                        if (total > done) s.append(" · ").append(eta((long) ((total - done) / bytesPerSec)));
                    }
                    detail.setText(s);
                }
            });
        }

        void dismiss() {
            try {
                dialog.dismiss();
            } catch (Throwable ignored) {
                // window already gone
            }
        }

        private void ui(Runnable r) {
            if (!cancelled) activity.runOnUiThread(r);
        }
    }

    /** Counts bytes read, honours Cancel, and refreshes the dialog a few times a second. */
    private static final class Progress extends FilterInputStream {
        private static final long TICK_MS = 300;
        private final long total;
        private final Ui ui;
        private final long started = SystemClock.elapsedRealtime();
        private long done;
        private long lastTick;
        private long windowStart = started;
        private long windowBytes;
        private double rate;

        Progress(InputStream in, long total, Ui ui) {
            super(in);
            this.total = total;
            this.ui = ui;
            ui.progress(0, total, 0);
        }

        @Override
        public int read() throws IOException {
            ui.check();
            int b = super.read();
            if (b >= 0) count(1);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            ui.check();
            int n = super.read(b, off, len);
            if (n > 0) count(n);
            return n;
        }

        private void count(int n) {
            done += n;
            windowBytes += n;
            long now = SystemClock.elapsedRealtime();
            if (now - lastTick < TICK_MS) return;
            lastTick = now;
            long span = now - windowStart;
            if (span >= 2000) {
                // Smoothed rate over ~2 s windows; the first window seeds it.
                double r = windowBytes * 1000.0 / span;
                rate = rate == 0 ? r : rate * 0.6 + r * 0.4;
                windowStart = now;
                windowBytes = 0;
            }
            ui.progress(done, total, now - started >= 2000 ? rate : 0);
        }
    }
}
