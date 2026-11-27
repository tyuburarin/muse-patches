package local.muse.extension.shared;

import android.app.Application;
import android.content.Context;

import java.lang.reflect.Method;

public class StringRef {
    /**
     * Resolves a string resource by name in the host app. When no resource
     * with that name exists, the identifier itself is returned, so passing a
     * literal label displays it as-is.
     */
    public static String str(String id) {
        try {
            Context context = currentApplication();
            if (context != null) {
                int resId = context.getResources().getIdentifier(id, "string", context.getPackageName());
                if (resId != 0) {
                    return context.getString(resId);
                }
            }
        } catch (Throwable ignored) {
        }
        return id;
    }

    private static Context currentApplication() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method method = activityThread.getMethod("currentApplication");
            return (Application) method.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
