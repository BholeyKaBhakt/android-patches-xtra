package io.github.bholeykabhakt.extension.bypassadbdetection;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.Settings;

/**
 * Settings.System counterpart of {@link GlobalShim}. Very old apps (pre-API-17)
 * still occasionally probe Settings.System for these keys; mirrored for
 * completeness.
 */
public final class SystemShim {
    private static final String SENTINEL = "_morphe_adb_bypass_dummy_";

    private SystemShim() {
    }

    private static boolean neutralize(String name) {
        return "adb_enabled".equals(name)
                || "adb_wifi_enabled".equals(name)
                || "development_settings_enabled".equals(name);
    }

    public static int getInt(ContentResolver cr, String name) throws Settings.SettingNotFoundException {
        if (neutralize(name)) return 0;
        return Settings.System.getInt(cr, name);
    }

    public static int getInt(ContentResolver cr, String name, int def) {
        if (neutralize(name)) return 0;
        return Settings.System.getInt(cr, name, def);
    }

    public static String getString(ContentResolver cr, String name) {
        if (neutralize(name)) return "0";
        return Settings.System.getString(cr, name);
    }

    public static long getLong(ContentResolver cr, String name) throws Settings.SettingNotFoundException {
        if (neutralize(name)) return 0L;
        return Settings.System.getLong(cr, name);
    }

    public static long getLong(ContentResolver cr, String name, long def) {
        if (neutralize(name)) return 0L;
        return Settings.System.getLong(cr, name, def);
    }

    public static float getFloat(ContentResolver cr, String name) throws Settings.SettingNotFoundException {
        if (neutralize(name)) return 0f;
        return Settings.System.getFloat(cr, name);
    }

    public static float getFloat(ContentResolver cr, String name, float def) {
        if (neutralize(name)) return 0f;
        return Settings.System.getFloat(cr, name, def);
    }

    public static Uri getUriFor(String name) {
        if (neutralize(name)) return Settings.System.getUriFor(SENTINEL);
        return Settings.System.getUriFor(name);
    }
}
