package openlight.co.camera.utils;

import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import openlight.co.lib.utils.FeatureManager;
import openlight.co.lib.utils.LogUtil;

/**
 * Keeps a copy of the stack trace where someone can actually read it.
 *
 * Light shipped a crash reporter that saves a trace, tries to upload it to HockeyApp
 * and deletes it when the upload fails. The service was shut down in 2019, so every
 * upload fails and every trace is deleted — the only thing that survives a crash is
 * `System.exit(2)` in the log, which says nothing about what threw. Two known crashes
 * are blocked on exactly this: the mode_test intent, and the stock app's "high" video
 * profile.
 *
 * So a handler is installed ahead of theirs: it appends the trace to a file on shared
 * storage and then hands the exception on, leaving Light's reporter to do whatever it
 * was going to do. Nothing about the app's behaviour changes; the trace simply stops
 * disappearing.
 */
public final class CrashTrace {

    private static final String TAG = "CrashTrace";
    private static final String FILE_NAME = "openlight-crash.log";

    /** How long after a wrap to look again; HockeyApp registers on its own schedule. */
    private static final long RECHECK_MS = 5000L;

    /** Our handler, kept so we can tell whether someone has since displaced it. */
    private static Thread.UncaughtExceptionHandler sOurs;
    private static boolean sSelfTested;

    private CrashTrace() {
    }

    /**
     * Safe to call more than once, and worth doing: HockeyApp registers its own handler
     * from BaseActivity later in the lifecycle and takes the default slot for itself. So
     * this re-wraps whenever it finds someone else on top, and always hands the exception
     * onward — Light's reporter still does whatever it was going to do.
     */
    public static synchronized void install() {
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        if (previous == sOurs && sOurs != null) {
            return;
        }

        sOurs = new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable error) {
                try {
                    write(thread, error);
                } catch (Throwable failed) {
                    // A crash inside the crash handler helps nobody, but silence here
                    // once cost an evening: say why the trace did not reach the file.
                    LogUtil.e(TAG, "Could not write the trace: " + failed);
                }
                if (previous != null) {
                    previous.uncaughtException(thread, error);
                }
            }
        };
        Thread.setDefaultUncaughtExceptionHandler(sOurs);

        // HockeyApp does not register from onResume directly — it goes through a handler
        // of its own and lands after we have wrapped, taking the default slot with it. So
        // look again shortly and wrap whatever ended up on top.
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                install();
            }
        }, RECHECK_MS);

        LogUtil.i(TAG, "Traces will be kept in " + file().getAbsolutePath());
        selfTestIfAsked();
    }

    /**
     * A safety net nobody has fallen into is not known to hold. With crash.selftest set
     * in features.prop the app throws once, a couple of seconds after startup, which
     * proves the trace reaches the file. Off by default, and the app is expected to die.
     */
    private static void selfTestIfAsked() {
        if (sSelfTested || !FeatureManager.get().getBoolean("crash.selftest", false)) {
            return;
        }
        sSelfTested = true;
        LogUtil.w(TAG, "crash.selftest is on — this build will throw on purpose");
        // On a thread of its own: something around the main message loop swallows what
        // is thrown there, so a main-thread throw proves nothing about the handler.
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(RECHECK_MS + 2500L);
                } catch (InterruptedException ignored) {
                }
                throw new IllegalStateException("crash.selftest: deliberate crash to test the trace file");
            }
        }, "crash-selftest").start();
    }

    private static File file() {
        return new File(Environment.getExternalStorageDirectory(), FILE_NAME);
    }

    private static void write(Thread thread, Throwable error) throws Exception {
        FileWriter out = new FileWriter(file(), true);
        try {
            PrintWriter writer = new PrintWriter(out);
            String when = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            writer.println();
            writer.println("=== " + when + "  thread " + thread.getName() + " ===");
            error.printStackTrace(writer);
            writer.flush();
        } finally {
            out.close();
        }
    }
}
