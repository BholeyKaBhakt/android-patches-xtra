package io.github.bholeykabhakt.extension.common;

import android.app.Activity;
import android.app.ProgressDialog;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

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
 * dump's {@code assets/}) installs the same; anything outside it is skipped. Optional SHA-256 is
 * computed while streaming and checked before the swap. Work runs on a background thread; the
 * {@link Callback} fires on the UI thread.
 */
@SuppressWarnings("deprecation") // ProgressDialog: the framework's only percent + size bar.
public final class ZipInstaller {

    public interface Callback {
        void onSuccess();

        void onFailure(String error);
    }

    /** Checks the staged tree before it replaces the current one; return an error or null. */
    public interface Validator {
        String check(File staged);
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
        final ProgressDialog pd = new ProgressDialog(activity);
        pd.setTitle(title);
        pd.setMessage("Please keep the app open…");
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(100);
        pd.setProgressNumberFormat("");
        pd.setCancelable(false);
        pd.show();

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
                        if (raw == null) throw new IOException("cannot open file");
                        total = sizeOf(activity, uri);
                    } else {
                        conn = open(url);
                        raw = conn.getInputStream();
                        total = conn.getContentLengthLong();
                    }
                    MessageDigest md = wantsSha(sha256) ? MessageDigest.getInstance("SHA-256") : null;
                    InputStream in = new Progress(new BufferedInputStream(raw, 1 << 16), total, activity, pd);
                    if (md != null) in = new DigestInputStream(in, md);
                    try {
                        extract(in, stage, roots);
                        drain(in); // central directory, so the digest covers the whole file
                    } finally {
                        in.close();
                    }
                    if (md != null && !hex(md.digest()).equalsIgnoreCase(sha256.trim())) {
                        throw new IOException("checksum mismatch");
                    }
                    String bad = validator == null ? null : validator.check(stage);
                    if (bad != null) throw new IOException(bad);

                    if (dest.exists() && !dest.renameTo(old)) throw new IOException("cannot replace current files");
                    if (!stage.renameTo(dest)) {
                        old.renameTo(dest);
                        throw new IOException("cannot move files into place");
                    }
                    deleteRecursive(old);
                    finish(activity, pd, cb, null);
                } catch (Exception e) {
                    deleteRecursive(stage);
                    String msg = e.getMessage();
                    finish(activity, pd, cb, msg == null ? e.getClass().getSimpleName() : msg);
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }, "zip-install").start();
    }

    private static HttpURLConnection open(String url) throws IOException {
        String current = url;
        for (int redirects = 0; redirects < 6; redirects++) {
            HttpURLConnection c = (HttpURLConnection) new URL(current).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(30000);
            c.setReadTimeout(60000);
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
                throw new IOException("HTTP " + code);
            }
            return c;
        }
        throw new IOException("too many redirects");
    }

    private static void extract(InputStream in, File stage, String[] roots) throws IOException {
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
            File out = new File(stage, name.substring(prefix.length()));
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
            files++;
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

    /** Decimal units, like download hosts report sizes. */
    public static String human(long bytes) {
        if (bytes < 0) return "?";
        double mb = bytes / 1_000_000.0;
        if (mb >= 1000.0) return String.format(Locale.US, "%.2f GB", mb / 1000.0);
        return String.format(Locale.US, "%.1f MB", mb);
    }

    private static void finish(final Activity activity, final ProgressDialog pd, final Callback cb, final String err) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    pd.dismiss();
                } catch (Throwable ignored) {
                }
                if (err == null) cb.onSuccess();
                else cb.onFailure(err);
            }
        });
    }

    /** Counts bytes read and drives the bar: percent left, read / total right. */
    private static final class Progress extends FilterInputStream {
        private final long total;
        private final Activity activity;
        private final ProgressDialog pd;
        private long done;
        private int lastPct = -1;

        Progress(InputStream in, final long total, Activity activity, final ProgressDialog pd) {
            super(in);
            this.total = total;
            this.activity = activity;
            this.pd = pd;
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    pd.setIndeterminate(total <= 0);
                    if (total > 0) pd.setProgressNumberFormat(human(0) + " / " + human(total));
                }
            });
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) count(1);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) count(n);
            return n;
        }

        private void count(int n) {
            done += n;
            if (total <= 0) return;
            final int pct = (int) Math.min(100, done * 100 / total);
            if (pct == lastPct) return;
            lastPct = pct;
            final long d = done;
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    pd.setProgress(pct);
                    pd.setProgressNumberFormat(human(d) + " / " + human(total));
                }
            });
        }
    }
}
