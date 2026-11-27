package local.muse.extension.crimera;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Trimmed standalone replacement for the small part of PikoUtils that the
 * entity reflection layer uses (logging, toast, debug file dump).
 */
public class PikoUtils {

    private static Context appContext;

    public static void init(Context context) {
        if (appContext == null && context != null) {
            appContext = context.getApplicationContext();
        }
    }

    public static Context getContext() {
        return appContext;
    }

    public static void toast(String msg) {
        try {
            if (appContext != null) {
                Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show();
            } else {
                Log.i("PikoUtils", msg);
            }
        } catch (Exception ignored) {
        }
    }

    public static void logger(Object e) {
        if (e instanceof Throwable) {
            Log.e("PikoUtils", "error", (Throwable) e);
        } else {
            Log.e("PikoUtils", String.valueOf(e));
        }
    }

    public static boolean pikoWriteFile(String fileName, String data, boolean append) {
        try {
            if (appContext == null) return false;
            File dir = appContext.getExternalFilesDir(null);
            if (dir == null) return false;
            File file = new File(dir, fileName);
            try (FileOutputStream out = new FileOutputStream(file, append)) {
                out.write(data.getBytes());
            }
            return true;
        } catch (Exception e) {
            logger(e);
            return false;
        }
    }
}
