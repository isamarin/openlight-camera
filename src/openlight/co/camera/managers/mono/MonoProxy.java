package openlight.co.camera.managers.mono;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.FileOutputStream;
import openlight.co.lib.exif.ExifInterface;
import openlight.co.lib.utils.LogUtil;

/**
 * Turns the proxy JPEG grey to match what the panchromatic module actually recorded.
 *
 * The file written beside an `.lri` is a 1.5 MP preview, not the photograph — the real
 * frame is assembled later from the container. In this mode the frame that matters is
 * the panchromatic plane, so a colour preview of it is simply wrong, and the mode looks
 * broken however carefully the documentation explains itself.
 *
 * The weights are the ones measured on this camera rather than the usual luminance
 * ones. A colour module's channel means on a scene came to R 76, G 108, B 49 once
 * normalised for gain, and the panchromatic module on the same frame read 233 — the
 * sum, not the weighted average. So the bands are summed equally here. Against a
 * luma-weighted grey that lifts blue by about three times and drops green by nearly
 * half, which is precisely the difference between a preview that lies about the
 * monochrome frame and one that does not.
 *
 * Weighting is done in linear light: the JPEG is gamma-encoded, and averaging encoded
 * values is not the same operation at all.
 */
public final class MonoProxy {

    private static final String TAG = "MonoProxy";
    private static final int QUALITY = 95;

    /** sRGB transfer, both ways, tabulated — 1.5 MP is a lot of pow() calls otherwise. */
    private static final float[] TO_LINEAR = new float[256];
    private static final int[] TO_SRGB = new int[4096];

    static {
        for (int i = 0; i < 256; i++) {
            float v = i / 255.0f;
            TO_LINEAR[i] = (v <= 0.04045f) ? (v / 12.92f)
                    : (float) Math.pow((v + 0.055f) / 1.055f, 2.4);
        }
        for (int i = 0; i < TO_SRGB.length; i++) {
            float v = i / (float) (TO_SRGB.length - 1);
            float s = (v <= 0.0031308f) ? (12.92f * v)
                    : (float) (1.055f * Math.pow(v, 1.0 / 2.4) - 0.055);
            TO_SRGB[i] = Math.round(s * 255.0f);
        }
    }

    private MonoProxy() {
    }

    /**
     * Rewrites the file in place. Quiet about failures: a preview that stayed colour is
     * a cosmetic problem, and the frame in the container is untouched either way.
     */
    public static void greyInPlace(String path) {
        if (path == null || !path.toLowerCase().endsWith(".jpg")) {
            return;
        }
        long started = System.currentTimeMillis();
        Bitmap bitmap = BitmapFactory.decodeFile(path);
        if (bitmap == null) {
            LogUtil.w(TAG, "Could not decode the proxy, leaving it colour: " + path);
            return;
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            float linear = (TO_LINEAR[(p >> 16) & 0xFF]
                    + TO_LINEAR[(p >> 8) & 0xFF]
                    + TO_LINEAR[p & 0xFF]) / 3.0f;
            int grey = TO_SRGB[Math.round(linear * (TO_SRGB.length - 1))];
            pixels[i] = (p & 0xFF000000) | (grey << 16) | (grey << 8) | grey;
        }

        Bitmap grey = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        bitmap.recycle();

        File file = new File(path);
        File temp = new File(file.getParentFile(), file.getName() + ".mono");
        try {
            FileOutputStream out = new FileOutputStream(temp);
            try {
                if (!grey.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)) {
                    throw new java.io.IOException("compress refused");
                }
            } finally {
                out.close();
                grey.recycle();
            }
            carryExif(path, temp.getAbsolutePath());
            if (!temp.renameTo(file)) {
                throw new java.io.IOException("could not replace the original");
            }
            LogUtil.i(TAG, "Proxy greyed in " + (System.currentTimeMillis() - started) + " ms: " + path);
        } catch (Exception e) {
            LogUtil.w(TAG, "Leaving the proxy colour: " + e.getMessage());
            temp.delete();
        }
    }

    /**
     * Re-encoding drops the metadata the saver had just written, and a preview with no
     * timestamp or orientation is worse than a colour one. Copies the tags that a
     * gallery and a person actually use.
     */
    private static void carryExif(String from, String to) {
        String[] tags = {
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
        };
        try {
            ExifInterface source = new ExifInterface(from);
            ExifInterface target = new ExifInterface(to);
            for (String tag : tags) {
                String value = source.getAttribute(tag);
                if (value != null) {
                    target.setAttribute(tag, value);
                }
            }
            target.saveAttributes();
        } catch (Exception e) {
            LogUtil.w(TAG, "Proxy kept its pixels but lost its metadata: " + e.getMessage());
        }
    }
}
