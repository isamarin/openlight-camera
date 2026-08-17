package openlight.astro.probe;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Журнал пробника: в logcat и в файл, чтобы результат читался по adb без гонки с logcat. */
final class Probe {
    static final String TAG = "ASTROPROBE";
    static final File LOG = new File("/sdcard/astroprobe.log");

    private static final SimpleDateFormat STAMP =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private Probe() {}

    static void say(String line) {
        Log.i(TAG, line);
        try (PrintWriter out = new PrintWriter(new FileWriter(LOG, true))) {
            out.println(STAMP.format(new Date()) + "  " + line);
        } catch (Throwable t) {
            Log.e(TAG, "журнал не пишется", t);
        }
    }

    static void fail(String line, Throwable t) {
        Log.e(TAG, line, t);
        try (PrintWriter out = new PrintWriter(new FileWriter(LOG, true))) {
            out.println(STAMP.format(new Date()) + "  ОШИБКА " + line + ": " + t);
            t.printStackTrace(out);
        } catch (Throwable ignored) {
            // Журнал — не причина падать.
        }
    }

    static void reset() {
        //noinspection ResultOfMethodCallIgnored
        LOG.delete();
    }
}
