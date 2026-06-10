package io.github.bholeykabhakt.extension.stellariumassetpack;

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
 */
public final class AssetShim {

    private static final String PACK_NAME = "asset_pack_extended";
    // The app's process CWD is /data/data/<pkg>/files, and Stellarium's native
    // fallback is "./asset_pack_extended/", i.e. this directory.
    private static final String PACK_DIR =
            "/data/data/com.noctuasoftware.stellarium_free/files/" + PACK_NAME;
    /**
     * Marker that the pack is fully present (the deep DSO index).
     */
    private static final String SENTINEL = "dso/index.data";

    private AssetShim() {
    }

    public static AssetPackLocation getPackLocation(String packName) {
        if (!PACK_NAME.equals(packName)) return null;
        if (!isPackPresent()) return null;
        return new ShimAssetPackLocation(PACK_DIR);
    }

    /**
     * True once the pack has been downloaded/extracted into the files dir.
     */
    public static boolean isPackPresent() {
        return new File(PACK_DIR, SENTINEL).exists();
    }
}
