package io.github.bholeykabhakt.extension.branding;

import android.app.Notification;
import android.content.Context;
import android.graphics.drawable.Icon;

/**
 * Every {@code Notification.Builder.setSmallIcon(...)} call in the app is rerouted here, so the
 * status-bar icon is the patched one whatever resource or {@link Icon} the app picked.
 */
@SuppressWarnings("unused")
public final class NotificationIcon {
    private static final String NAME = "morphe_brand_notification";
    private static int id = -1;

    private NotificationIcon() {
    }

    private static int id() {
        if (id == -1) {
            id = 0;
            try {
                Context c = (Context) Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication").invoke(null);
                if (c != null) id = c.getResources().getIdentifier(NAME, "drawable", c.getPackageName());
            } catch (Throwable ignored) {
                // keep the app's own icon
            }
        }
        return id;
    }

    public static Notification.Builder setSmallIcon(Notification.Builder b, int icon) {
        int ours = id();
        return b.setSmallIcon(ours != 0 ? ours : icon);
    }

    public static Notification.Builder setSmallIcon(Notification.Builder b, int icon, int level) {
        int ours = id();
        return ours != 0 ? b.setSmallIcon(ours) : b.setSmallIcon(icon, level);
    }

    public static Notification.Builder setSmallIcon(Notification.Builder b, Icon icon) {
        int ours = id();
        return ours != 0 ? b.setSmallIcon(ours) : b.setSmallIcon(icon);
    }
}
