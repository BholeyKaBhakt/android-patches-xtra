package com.google.android.play.core.assetpacks;

import com.google.android.play.core.assetpacks.model.AssetPackStorageMethod;

/**
 * A fake {@link AssetPackLocation} pointing at a local directory the app already
 * controls, so Stellarium's native engine loads the extended catalog from there
 * without Google Play Asset Delivery ever installing the pack.
 *
 * <p>Lives in the {@code com.google.android.play.core.assetpacks} package so it
 * can reach {@code AssetPackLocation}'s package-private constructor. The Play
 * Core native bridge (libplaycore.so) calls {@link #assetsPath()} /
 * {@link #packStorageMethod()} through virtual JNI dispatch, so these overrides
 * are what the engine sees.
 */
public final class ShimAssetPackLocation extends AssetPackLocation {

    private final String dir;

    public ShimAssetPackLocation(String dir) {
        this.dir = dir;
    }

    // STORAGE_FILES → engine reads from assetsPath() (the extracted files dir).
    @Override
    public int packStorageMethod() {
        return AssetPackStorageMethod.STORAGE_FILES;
    }

    @Override
    public String path() {
        return dir;
    }

    @Override
    public String assetsPath() {
        return dir;
    }
}
