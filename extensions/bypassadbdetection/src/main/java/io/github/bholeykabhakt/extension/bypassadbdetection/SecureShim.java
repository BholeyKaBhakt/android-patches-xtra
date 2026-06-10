package io.github.bholeykabhakt.extension.bypassadbdetection;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.Settings;

/**
 * Settings.Secure counterpart of {@link GlobalShim}. ADB and Developer Options
 * keys lived in Settings.Secure on older Android (API &lt; 17 / 24). Apps still
 * call these as a fallback or as a paranoia layer; we cover them identically.
 */
public final class SecureShim {
    private static final String SENTINEL = "_morphe_adb_bypass_dummy_";

    private SecureShim() {
    }

    private static boolean neutralize(String name) {
        return "adb_enabled".equals(name)
                || "adb_wifi_enabled".equals(name)
                || "development_settings_enabled".equals(name);
    }

    public static int getInt(ContentResolver cr, String name) throws Settings.SettingNotFoundException {
        if (neutralize(name)) return 0;
        return Settings.Secure.getInt(cr, name);
    }

    public static int getInt(ContentResolver cr, String name, int def) {
        if (neutralize(name)) return 0;
        return Settings.Secure.getInt(cr, name, def);
    }

    public static String getString(ContentResolver cr, String name) {
        if (neutralize(name)) return "0";
        return Settings.Secure.getString(cr, name);
    }

    public static long getLong(ContentResolver cr, String name) throws Settings.SettingNotFoundException {
        if (neutralize(name)) return 0L;
        return Settings.Secure.getLong(cr, name);
    }

    public static long getLong(ContentResolver cr, String name, long def) {
        if (neutralize(name)) return 0L;
        return Settings.Secure.getLong(cr, name, def);
    }

    public static float getFloat(ContentResolver cr, String name) throws Settings.SettingNotFoundException {
        if (neutralize(name)) return 0f;
        return Settings.Secure.getFloat(cr, name);
    }

    public static float getFloat(ContentResolver cr, String name, float def) {
        if (neutralize(name)) return 0f;
        return Settings.Secure.getFloat(cr, name, def);
    }

    public static Uri getUriFor(String name) {
        if (neutralize(name)) return Settings.Secure.getUriFor(SENTINEL);
        return Settings.Secure.getUriFor(name);
    }
}
