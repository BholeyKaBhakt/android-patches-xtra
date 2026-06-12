package io.github.bholeykabhakt.extension.common;

import android.app.Activity;
import android.app.ProgressDialog;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Generic "download a .zip and extract it" helper with a progress dialog — app-agnostic,
 * so any extension can reuse it (only the orchestration around it, e.g. when to prompt
 * and what to do on success, is app-specific).
 *
 * <p>{@link #run} downloads from a URL (following redirects) behind a horizontal progress
 * dialog (left = percent, right = downloaded / total in decimal MB), optionally verifies a
 * SHA-256, and extracts <b>atomically</b> — staging into a sibling {@code <name>.tmp} dir
 * and renaming into place — so the destination never exists half-written. All work runs on
 * a background thread; the {@link Callback} fires on the UI thread.
 *
 * <p>Lives in {@code …extension.common}; promote to a shared extension module if a second
 * consumer appears (the package name is already neutral, so no import churn).
 */
// ProgressDialog is deprecated but intentional here: it is the one framework widget that
// renders the exact "percent on the left / size on the right" determinate layout this needs,
// with no extra resources or androidx dependency in the merged extension.
@SuppressWarnings("deprecation")
public final class ZipDownloader {

    private ZipDownloader() {
    }

    /**
     * @param sha256  lowercase hex SHA-256 to verify the archive against, or null/empty to skip.
     * @param destDir directory to extract into (replaced atomically on success).
     * @param title   progress-dialog title, e.g. {@code "Downloading catalog"}.
     */
    public static void run(final Activity activity, final String url, final String sha256,
                           final File destDir, final String title, final Callback cb) {
        final ProgressDialog pd = new ProgressDialog(activity);
        pd.setTitle(title);
        pd.setMessage("Please keep the app open…");
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(100);
        pd.setProgressNumberFormat(""); // suppress the default "0/100" flash until the size is known
        pd.setIndeterminate(false);
        pd.setCancelable(false);
        pd.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                File tmp = new File(activity.getCacheDir(), destDir.getName() + ".dl.zip");
                try {
                    downloadTo(url, tmp, activity, pd);

                    if (sha256 != null && !sha256.trim().isEmpty()) {
                        setStatus(activity, pd, "Verifying…");
                        if (!sha256Hex(tmp).equalsIgnoreCase(sha256.trim())) {
                            throw new IOException("checksum mismatch");
                        }
                    }

                    setStatus(activity, pd, "Extracting…");
                    // Stage into a sibling dir, then atomically rename into place, so destDir
                    // never exists in a half-extracted state (a presence check on any file
                    // inside it would otherwise accept a broken extract).
                    File stage = new File(destDir.getParentFile(), destDir.getName() + ".tmp");
                    deleteRecursive(stage);
                    extractZip(tmp, stage);
                    deleteRecursive(destDir);
                    if (!stage.renameTo(destDir)) throw new IOException("publish (rename) failed");
                    tmp.delete();

                    finish(activity, pd, cb, null);
                } catch (Exception e) {
                    tmp.delete();
                    finish(activity, pd, cb, String.valueOf(e.getMessage()));
                }
            }
        }, "zip-download").start();
    }

    private static void downloadTo(String url, File out, final Activity activity, final ProgressDialog pd)
            throws IOException {
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
            final long total = c.getContentLengthLong();
            if (pd != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        pd.setIndeterminate(total <= 0);
                        // Right side of the bar: actual size (downloaded / total) instead
                        // of the default "progress/max" duplicate of the left-side percent.
                        if (total > 0) pd.setProgressNumberFormat(human(0) + " / " + human(total));
                    }
                });
            }
            InputStream in = new BufferedInputStream(c.getInputStream());
            OutputStream fo = new FileOutputStream(out);
            byte[] buf = new byte[1 << 16];
            long done = 0;
            int n;
            int lastPct = -1;
            while ((n = in.read(buf)) != -1) {
                fo.write(buf, 0, n);
                done += n;
                if (total > 0) {
                    final int pct = (int) (done * 100 / total);
                    if (pct != lastPct) {
                        lastPct = pct;
                        final long d = done;
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                pd.setProgress(pct);                                  // left: %
                                pd.setProgressNumberFormat(human(d) + " / " + human(total)); // right: size
                            }
                        });
                    }
                }
            }
            fo.flush();
            fo.close();
            in.close();
            c.disconnect();
            return;
        }
        throw new IOException("too many redirects");
    }

    private static void extractZip(File zip, File destDir) throws IOException {
        if (!destDir.exists() && !destDir.mkdirs()) throw new IOException("mkdir failed");
        String destCanon = destDir.getCanonicalPath() + File.separator;
        ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)));
        ZipEntry e;
        byte[] buf = new byte[1 << 16];
        while ((e = zin.getNextEntry()) != null) {
            File outFile = new File(destDir, e.getName());
            if (!outFile.getCanonicalPath().startsWith(destCanon)) {
                throw new IOException("bad entry " + e.getName()); // zip-slip guard
            }
            if (e.isDirectory()) {
                outFile.mkdirs();
            } else {
                File parent = outFile.getParentFile();
                if (parent != null) parent.mkdirs();
                OutputStream fo = new FileOutputStream(outFile);
                int n;
                while ((n = zin.read(buf)) != -1) fo.write(buf, 0, n);
                fo.close();
            }
            zin.closeEntry();
        }
        zin.close();
    }

    /**
     * Human-readable byte count in <b>decimal</b> units (1 MB = 1e6 bytes), e.g.
     * {@code "120.4 MB"} / {@code "1.23 GB"} — matches how download hosts (GitHub) report
     * size, so a hard-coded "~N MB" estimate and the live total don't disagree.
     */
    private static String human(long bytes) {
        if (bytes < 0) return "?";
        double mb = bytes / 1_000_000.0;
        if (mb >= 1000.0) return String.format(Locale.US, "%.2f GB", mb / 1000.0);
        return String.format(Locale.US, "%.1f MB", mb);
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        f.delete();
    }

    private static String sha256Hex(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        InputStream in = new BufferedInputStream(new FileInputStream(f));
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
        in.close();
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format(Locale.US, "%02x", b));
        return sb.toString();
    }

    private static void setStatus(final Activity activity, final ProgressDialog pd, final String msg) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pd.setIndeterminate(true);
                pd.setMessage(msg);
            }
        });
    }

    private static void finish(final Activity activity, final ProgressDialog pd,
                               final Callback cb, final String err) {
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

    /**
     * Result of a {@link #run} call, delivered on the UI thread.
     */
    public interface Callback {
        void onSuccess();

        void onFailure(String error);
    }
}
