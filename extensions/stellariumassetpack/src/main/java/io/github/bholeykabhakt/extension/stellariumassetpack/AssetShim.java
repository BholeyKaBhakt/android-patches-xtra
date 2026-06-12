package io.github.bholeykabhakt.extension.stellariumassetpack;

import android.content.Context;

import com.google.android.play.core.assetpacks.AssetPackLocation;
import com.google.android.play.core.assetpacks.ShimAssetPackLocation;

import java.io.File;

/**
 * Entry point invoked from patched {@code AssetPackManager.getPackLocation()}.
 *
 * <p>Returns a fake "installed" location for {@code asset_pack_extended} once the
 * pack has been downloaded/extracted into the app's private files dir (by
 * {@link CatalogDownloader}); otherwise {@code null} — identical to the unpatched
 * not-installed result, so the native code behaves normally until the pack lands.
 *
 * <p>The pack dir is resolved from the running app's {@link Context#getFilesDir()},
 * never a hard-coded {@code /data/data/<pkg>} path: under a secondary user or work
 * profile the real files dir is {@code /data/user/<id>/<pkg>/files}, so a literal
 * {@code /data/data} (= user 0) would point at the wrong user and never find the pack.
 */
public final class AssetShim {

    private static final String PACK_NAME = "asset_pack_extended";
    /**
     * Marker that the pack is fully present (the deep DSO index).
     */
    private static final String SENTINEL = "dso/index.data";

    private static volatile Context appContext;

    private AssetShim() {
    }

    public static AssetPackLocation getPackLocation(String packName) {
        if (!PACK_NAME.equals(packName)) return null;
        File dir = packDir();
        if (dir == null || !new File(dir, SENTINEL).exists()) return null;
        return new ShimAssetPackLocation(dir.getPath());
    }

    /**
     * True once the pack has been downloaded/extracted into the files dir.
     */
    public static boolean isPackPresent() {
        File dir = packDir();
        return dir != null && new File(dir, SENTINEL).exists();
    }

    /**
     * {@code <filesDir>/asset_pack_extended}, or null before the app context exists.
     */
    private static File packDir() {
        Context ctx = appContext();
        return ctx == null ? null : new File(ctx.getFilesDir(), PACK_NAME);
    }

    /**
     * The application Context, obtained without one being passed in — the native engine
     * calls {@link #getPackLocation} during early startup, before any patched Activity
     * hook runs. {@code ActivityThread.currentApplication()} is a stable greylisted API
     * and is already non-null by the time the engine initialises. Cached after first use.
     */
    private static Context appContext() {
        Context c = appContext;
        if (c != null) return c;
        try {
            c = (Context) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            appContext = c;
        } catch (Throwable ignored) {
            // Application not yet attached, or API unavailable — caller treats null as
            // "pack not present", which is the correct not-installed behaviour.
        }
        return c;
    }
}
