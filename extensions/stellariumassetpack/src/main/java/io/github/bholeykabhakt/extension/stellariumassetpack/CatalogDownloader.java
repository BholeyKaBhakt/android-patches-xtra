package io.github.bholeykabhakt.extension.stellariumassetpack;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.File;

import io.github.bholeykabhakt.extension.common.ZipDownloader;

/**
 * Stellarium-specific glue around the generic {@link ZipDownloader}: a first-launch
 * prompt to download the extended catalog and extract it into
 * {@code files/asset_pack_extended/}. The Play-Core shim ({@link AssetShim}) then
 * reports the pack installed — no Google Play, account, or root.
 *
 * <p>The engine loads the pack only at startup (its {@code data_packs_on_resume} just
 * "updates info", it does not re-run the loader), so on success this offers
 * <b>Restart now</b> ({@link #restart}) to relaunch and load.
 *
 * <p>Set the download URL / SHA-256 via {@link #configUrl()} / {@link #configSha()}
 * (SHA empty = skipped). The actual download/progress/extract lives in
 * {@link ZipDownloader}.
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
        String sha = configSha();
        if (sha != null && sha.startsWith("__MORPHE_")) sha = null;

        File dest = new File(activity.getFilesDir(), "asset_pack_extended");
        ZipDownloader.run(activity, url, sha, dest, "Downloading catalog", new ZipDownloader.Callback() {
            @Override
            public void onSuccess() {
                showInstalled(activity);
            }

            @Override
            public void onFailure(String error) {
                handled = false; // allow retry next resume
                Toast.makeText(activity, "Catalog download failed: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private static void showInstalled(final Activity activity) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle("Catalog installed")
                .setMessage("The deep catalog is ready. Restart the app to load it.")
                .setCancelable(false)
                .setNegativeButton("Later", null)
                .setPositiveButton("Restart now", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        restart(activity);
                    }
                })
                .show();
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
