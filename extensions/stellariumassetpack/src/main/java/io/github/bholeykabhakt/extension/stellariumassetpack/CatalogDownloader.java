package io.github.bholeykabhakt.extension.stellariumassetpack;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.DateFormat;
import java.util.Collections;
import java.util.Date;

import io.github.bholeykabhakt.extension.common.ZipInstaller;

/**
 * The extended catalog dialog: download it, import a zip the user already has, or remove it.
 * Shown once on first launch while the pack is missing, and any time from the launcher
 * long-press shortcut this class publishes. The engine loads the pack only at startup, so a
 * change offers <b>Restart now</b>. {@link AssetShim} makes the engine see the installed files.
 */
@SuppressWarnings("deprecation") // android.app.Fragment: gets the picker result without hooking the Qt activity.
public final class CatalogDownloader {

    private static final String EXTRA_OPEN = "io.github.bholeykabhakt.stellarium.CATALOG";
    private static final String SHORTCUT_ID = "deep_sky_catalog";
    private static final String PREFS = "stellarium_catalog";
    private static final String PREF_PROMPTED = "prompted";
    private static final String PICKER_TAG = "catalog_picker";
    private static final String[] ROOTS = {"dso", "stars", "surveys", "README", ZipInstaller.LABEL_FILE};
    private static final String DOWNLOAD_SIZE = "258 MB";
    private static final String INSTALLED_SIZE = "293 MB";

    private static volatile boolean resumedOnce = false;

    private CatalogDownloader() {
    }

    // Empty URL hides Download; empty SHA skips the check (the release asset is replaced in place).
    public static String configUrl() {
        return "https://github.com/BholeyKaBhakt/strl-data-pack/releases/latest/download/st-xtra-data.zip";
    }

    public static String configSha() {
        return "";
    }

    /** Injected at the start of the main activity's onResume(). */
    public static void maybePrompt(final Activity activity) {
        if (consumeOpenExtra(activity.getIntent())) {
            post(activity);
            return;
        }
        if (resumedOnce) return;
        resumedOnce = true;
        publishShortcut(activity);

        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (AssetShim.isPackPresent() || prefs.getBoolean(PREF_PROMPTED, false)) return;
        prefs.edit().putBoolean(PREF_PROMPTED, true).apply();
        post(activity);
    }

    /** Injected at the start of the Qt activity's onNewIntent(): the shortcut on a running app. */
    public static void onNewIntent(Activity activity, Intent intent) {
        if (consumeOpenExtra(intent)) post(activity);
    }

    private static boolean consumeOpenExtra(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_OPEN, false)) return false;
        intent.removeExtra(EXTRA_OPEN);
        return true;
    }

    private static void post(final Activity activity) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                showMenu(activity);
            }
        });
    }

    private static void publishShortcut(Activity activity) {
        try {
            ShortcutManager sm = activity.getSystemService(ShortcutManager.class);
            if (sm == null) return;
            Context app = activity.getApplicationContext();
            Intent open = new Intent(Intent.ACTION_MAIN)
                    .setClassName(app.getPackageName(), activity.getClass().getName())
                    .putExtra(EXTRA_OPEN, true);
            ShortcutInfo info = new ShortcutInfo.Builder(app, SHORTCUT_ID)
                    .setShortLabel("Deep sky catalog")
                    .setIcon(Icon.createWithResource(app, app.getApplicationInfo().icon))
                    .setIntent(open)
                    .build();
            sm.addDynamicShortcuts(Collections.singletonList(info));
        } catch (Throwable ignored) {
            // Launcher without shortcut support, or the dynamic-shortcut limit is reached.
        }
    }

    private static void showMenu(final Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        final boolean installed = AssetShim.isPackPresent();
        final String url = configUrl();
        final Context ctx = new ContextThemeWrapper(activity, ZipInstaller.THEME);

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, ZipInstaller.dp(ctx, 4), 0, 0);

        final TextView status = text(ctx, installed ? "Installed" : "Not installed", 14, 1f);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        status.setTextColor(installed ? 0xFF81C995 : 0xFFFDD663);
        pad(ctx, status, 4);
        box.addView(status);
        TextView about = text(ctx, "Adds stars down to magnitude 12, deep-sky objects and nebula imagery. "
                + "Everything is stored on the device and works offline.", 14, 0.75f);
        pad(ctx, about, 12);
        box.addView(about);

        final AlertDialog dialog = new AlertDialog.Builder(activity, ZipInstaller.THEME)
                .setTitle("Deep sky catalog")
                .setView(box)
                .setNegativeButton(installed ? "Close" : "Not now", null)
                .create();

        if (url != null && !url.isEmpty()) {
            box.addView(action(ctx, dialog, installed ? "Download again" : "Download",
                    installed ? "Replace it with the latest release" : DOWNLOAD_SIZE + " download, about "
                            + INSTALLED_SIZE + " once unpacked",
                    new Runnable() {
                        @Override
                        public void run() {
                            download(activity, url);
                        }
                    }));
        }
        box.addView(action(ctx, dialog, "Import from a file", "Pick a catalog .zip already on this device",
                new Runnable() {
                    @Override
                    public void run() {
                        pick(activity);
                    }
                }));
        if (installed) {
            box.addView(action(ctx, dialog, "Remove", "Free up the space it uses", new Runnable() {
                @Override
                public void run() {
                    confirmRemove(activity);
                }
            }));
        }
        dialog.show();

        if (installed) {
            // Size and date need a walk over ~7k files; fill them in when ready.
            final File dir = packDir(activity);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final long bytes = ZipInstaller.sizeOnDisk(dir);
                    final String version = ZipInstaller.label(dir);
                    final long when = new File(dir, AssetShim.SENTINEL).lastModified();
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String date = when > 0 ? " · added " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(when)) : "";
                            status.setText("Installed" + (version == null ? "" : " · " + version)
                                    + " · " + ZipInstaller.human(bytes) + date);
                        }
                    });
                }
            }, "catalog-size").start();
        }
    }

    private static TextView text(Context ctx, String s, int sp, float alpha) {
        TextView t = new TextView(ctx);
        t.setTextAppearance(android.R.style.TextAppearance_DeviceDefault);
        t.setTextSize(sp);
        t.setAlpha(alpha);
        t.setText(s);
        return t;
    }

    private static void pad(Context ctx, View v, int bottomDp) {
        int side = ZipInstaller.dp(ctx, 24);
        v.setPadding(side, 0, side, ZipInstaller.dp(ctx, bottomDp));
    }

    /** A tappable two-line row: what it does and what it costs. */
    private static View action(Context ctx, final AlertDialog dialog, String title, String hint, final Runnable run) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        int side = ZipInstaller.dp(ctx, 24);
        int vert = ZipInstaller.dp(ctx, 10);
        row.setPadding(side, vert, side, vert);
        row.setMinimumHeight(ZipInstaller.dp(ctx, 56));
        TypedValue tv = new TypedValue();
        if (ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            row.setBackgroundResource(tv.resourceId);
        }
        row.setClickable(true);
        TextView t = text(ctx, title, 16, 1f);
        TypedValue accent = new TypedValue();
        if (ctx.getTheme().resolveAttribute(android.R.attr.colorAccent, accent, true)) t.setTextColor(accent.data);
        row.addView(t);
        row.addView(text(ctx, hint, 13, 0.65f));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                run.run();
            }
        });
        return row;
    }

    private static File packDir(Activity activity) {
        return new File(activity.getFilesDir(), AssetShim.PACK_NAME);
    }

    private static void download(final Activity activity, final String url) {
        ZipInstaller.fromUrl(activity, url, configSha(), packDir(activity), ROOTS, VALIDATOR,
                "Downloading catalog", result(activity, "Download failed", new Runnable() {
                    @Override
                    public void run() {
                        download(activity, url);
                    }
                }));
    }

    static void importUri(final Activity activity, Uri uri) {
        ZipInstaller.fromUri(activity, uri, packDir(activity), ROOTS, VALIDATOR,
                "Importing catalog", result(activity, "Import failed", new Runnable() {
                    @Override
                    public void run() {
                        pick(activity);
                    }
                }));
    }

    private static final ZipInstaller.Validator VALIDATOR = new ZipInstaller.Validator() {
        @Override
        public String check(File staged) {
            if (!new File(staged, AssetShim.SENTINEL).isFile() || !new File(staged, "stars").isDirectory()) {
                return "this .zip is not a Stellarium deep sky catalog";
            }
            return null;
        }
    };

    private static ZipInstaller.Callback result(final Activity activity, final String failure, final Runnable retry) {
        return new ZipInstaller.Callback() {
            @Override
            public void onSuccess() {
                String version = ZipInstaller.label(packDir(activity));
                offerRestart(activity, "Catalog installed", (version == null ? "The catalog" : "Catalog " + version)
                        + " is ready. Restart Stellarium to load it.");
            }

            @Override
            public void onFailure(String error) {
                if (error == null) {
                    Toast.makeText(activity, "Cancelled", Toast.LENGTH_SHORT).show();
                    return;
                }
                new AlertDialog.Builder(activity, ZipInstaller.THEME)
                        .setTitle(failure)
                        .setMessage(capitalize(error) + ".\n\nThe installed catalog, if any, was not changed.")
                        .setNegativeButton("Close", null)
                        .setPositiveButton("Try again", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                retry.run();
                            }
                        })
                        .show();
            }
        };
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static void pick(Activity activity) {
        FragmentManager fm = activity.getFragmentManager();
        Fragment old = fm.findFragmentByTag(PICKER_TAG);
        if (old != null) fm.beginTransaction().remove(old).commitNowAllowingStateLoss();
        fm.beginTransaction().add(new Picker(), PICKER_TAG).commitNowAllowingStateLoss();
    }

    private static void confirmRemove(final Activity activity) {
        new AlertDialog.Builder(activity, ZipInstaller.THEME)
                .setTitle("Remove the catalog?")
                .setMessage("Deep stars, deep-sky objects and nebula imagery go away until you download "
                        + "or import it again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        remove(activity);
                    }
                })
                .show();
    }

    private static void remove(Activity activity) {
        File dir = packDir(activity);
        final File doomed = new File(dir.getParentFile(), dir.getName() + ".old");
        // Rename first so the shim stops reporting the pack at once; delete in the background.
        ZipInstaller.deleteRecursive(doomed);
        if (dir.exists() && !dir.renameTo(doomed)) {
            Toast.makeText(activity, "Could not remove the catalog", Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                ZipInstaller.deleteRecursive(doomed);
            }
        }, "catalog-remove").start();
        offerRestart(activity, "Catalog removed", "Restart Stellarium to finish removing it.");
    }

    private static void offerRestart(final Activity activity, String title, String message) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        new AlertDialog.Builder(activity, ZipInstaller.THEME)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setNegativeButton("Later", null)
                .setPositiveButton("Restart now", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        restart(activity);
                    }
                })
                .show();
    }

    /** Relaunch fresh so the native engine re-inits and (un)loads the pack. */
    private static void restart(Activity activity) {
        Context ctx = activity.getApplicationContext();
        Intent intent = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            ctx.startActivity(intent);
        }
        Runtime.getRuntime().exit(0);
    }

    /** Headless fragment that owns the document-picker request and its result. */
    public static final class Picker extends Fragment {
        private static final int REQUEST = 0x5D5C;

        @Override
        public void onCreate(Bundle state) {
            super.onCreate(state);
            if (state != null) return; // result still pending from before a recreate
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*")
                    .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                            "application/zip", "application/x-zip-compressed", "application/octet-stream"});
            try {
                startActivityForResult(pick, REQUEST);
            } catch (Exception e) {
                Toast.makeText(getActivity(), "No file picker available", Toast.LENGTH_LONG).show();
                done();
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode != REQUEST) return;
            Activity activity = getActivity();
            done();
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null && activity != null) {
                importUri(activity, data.getData());
            }
        }

        private void done() {
            FragmentManager fm = getFragmentManager();
            if (fm != null) fm.beginTransaction().remove(this).commitAllowingStateLoss();
        }
    }
}
