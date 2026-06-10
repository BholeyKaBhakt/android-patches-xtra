package io.github.bholeykabhakt.extension.stellariumassetpack;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * First-launch dialog that downloads the extended catalog from an external URL
 * and extracts it into {@code files/asset_pack_extended/}. The Play-Core shim
 * ({@link AssetShim}) then reports the pack installed — no Google Play, account,
 * or root.
 *
 * <p>The engine loads the pack only at startup (its {@code data_packs_on_resume}
 * just "updates info", it does not re-run the loader), so on success the dialog
 * offers <b>Restart now</b> ({@link #restart}) to relaunch and load.
 *
 * <p>Set the download URL / SHA-256 by editing {@link #configUrl()} /
 * {@link #configSha()} and rebuilding (SHA empty = skipped).
 */
public final class CatalogDownloader {

    private static volatile boolean handled = false;

    private CatalogDownloader() {
    }

    // Edit these for the real release, then rebuild. Empty/"__MORPHE_" disables.
    // SHA skipped (empty) because the release asset is updated frequently.
    public static String configUrl() {
        return "https://github.com/BholeyKaBhakt/strl-data-pack/releases/latest/download/st-xtra-data.zip";
    }

    public static String configSha() {
        return "";
    }

    /**
     * Injected at the start of the main activity's onResume().
     */
    public static void maybePrompt(final Activity activity) {
        if (handled) return;
        handled = true;

        if (AssetShim.isPackPresent()) return; // already installed

        final String url = configUrl();
        if (url == null || url.isEmpty() || url.startsWith("__MORPHE_"))
            return; // no URL configured

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                showPrompt(activity, url);
            }
        });
    }

    private static void showPrompt(final Activity activity, final String url) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle("Deep sky catalog")
                .setMessage("Download the extended catalog (~256 MB) for deep stars, "
                        + "deep-sky objects and nebula imagery? It is saved on this device "
                        + "and used offline.")
                .setCancelable(true)
                .setNegativeButton("Not now", null)
                .setPositiveButton("Download", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        startDownload(activity, url);
                    }
                })
                .show();
    }

    private static void startDownload(final Activity activity, final String url) {
        final ProgressDialog pd = new ProgressDialog(activity);
        pd.setTitle("Downloading catalog");
        pd.setMessage("Please keep the app open…");
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(100);
        pd.setIndeterminate(false);
        pd.setCancelable(false);
        pd.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                File tmp = new File(activity.getCacheDir(), "asset_pack_extended.zip");
                try {
                    downloadTo(url, tmp, activity, pd);

                    String expected = configSha();
                    if (expected != null && !expected.isEmpty() && !expected.startsWith("__MORPHE_")) {
                        setStatus(activity, pd, "Verifying…");
                        if (!sha256Hex(tmp).equalsIgnoreCase(expected.trim())) {
                            throw new IOException("checksum mismatch");
                        }
                    }

                    setStatus(activity, pd, "Extracting…");
                    File dir = new File(activity.getFilesDir(), "asset_pack_extended");
                    extractZip(tmp, dir);
                    tmp.delete();

                    finish(activity, pd, true, null);
                } catch (Exception e) {
                    tmp.delete();
                    handled = false; // allow retry next resume
                    finish(activity, pd, false, String.valueOf(e.getMessage()));
                }
            }
        }, "ep-download").start();
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
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                pd.setProgress(pct);
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

    private static String sha256Hex(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        InputStream in = new BufferedInputStream(new FileInputStream(f));
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
        in.close();
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
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
                               final boolean ok, final String err) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    pd.dismiss();
                } catch (Throwable ignored) {
                }
                if (ok) {
                    new AlertDialog.Builder(activity)
                            .setTitle("Catalog installed")
                            .setMessage("The deep catalog is ready. Restart the app to load it.")
                            .setCancelable(false)
                            .setNegativeButton("Later", null)
                            .setPositiveButton("Restart now",
                                    new android.content.DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(android.content.DialogInterface d, int w) {
                                            restart(activity);
                                        }
                                    })
                            .show();
                } else {
                    Toast.makeText(activity, "Catalog download failed: " + err, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /**
     * Relaunch the app fresh so the native engine re-inits and loads the pack.
     */
    private static void restart(Activity activity) {
        Context ctx = activity.getApplicationContext();
        Intent intent = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            ctx.startActivity(intent);
        }
        Runtime.getRuntime().exit(0);
    }
}
