package io.github.bholeykabhakt.extension.bypassadbdetection;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.Settings;

/**
 * Drop-in replacement for {@link android.provider.Settings.Global} static getters.
 * Every invoke-static call to Settings$Global in the patched APK is rewritten by
 * BypassAdbDetectionPatch to land here. We inspect the runtime key argument: if
 * it names ADB or Developer-Options state, we return a neutral "off" value;
 * otherwise we delegate to the real framework call so unrelated settings behave
 * identically.
 * <p>
 * Signatures (return types and parameters) mirror Settings$Global exactly so the
 * invoke-static reference swap requires no register or stack adjustments.
 */
public final class GlobalShim {
    // Non-existent setting key. content://settings/global/<this> yields zero rows,
    // so observers never fire and ContentResolver.query reads behave like "unset".
    private static final String SENTINEL = "_morphe_adb_bypass_dummy_";

    private GlobalShim() {
    }

    private static boolean neutralize(String name) {
        return "adb_enabled".equals(name)
                || "adb_wifi_enabled".equals(name)
                || "development_settings_enabled".equals(name);
    }

    public static int getInt(ContentResolver cr, String name) throws Settings.SettingNotFoundException {
        if (neutralize(name)) return 0;
        return Settings.Global.getInt(cr, name);
    }

    public static int getInt(ContentResolver cr, String name, int def) {
        if (neutralize(name)) return 0;
        return Settings.Global.getInt(cr, name, def);
    }

    public static String getString(ContentResolver cr, String name) {
        if (neutralize(name)) return "0";
        return Settings.Global.getString(cr, name);
    }

    public static long getLong(ContentResolver cr, String name) throws Settings.SettingNotFoundException {
        if (neutralize(name)) return 0L;
        return Settings.Global.getLong(cr, name);
    }

    public static long getLong(ContentResolver cr, String name, long def) {
        if (neutralize(name)) return 0L;
        return Settings.Global.getLong(cr, name, def);
    }

    public static float getFloat(ContentResolver cr, String name) throws Settings.SettingNotFoundException {
        if (neutralize(name)) return 0f;
        return Settings.Global.getFloat(cr, name);
    }

    public static float getFloat(ContentResolver cr, String name, float def) {
        if (neutralize(name)) return 0f;
        return Settings.Global.getFloat(cr, name, def);
    }

    public static Uri getUriFor(String name) {
        if (neutralize(name)) return Settings.Global.getUriFor(SENTINEL);
        return Settings.Global.getUriFor(name);
    }
}
