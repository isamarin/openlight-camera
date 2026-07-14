package openlight.co.lib.utils;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class FeatureManager {
    private static final String FEATURE_PROP_FILE_NAME = "features.prop";
    private static final String TAG = "FeatureManager";

    private static final FeatureManager sInstance = new FeatureManager();

    private final Properties mProperties = new Properties();

    private FeatureManager() {
        loadFromFile(new File(Environment.getExternalStorageDirectory(), FEATURE_PROP_FILE_NAME));
    }

    public static FeatureManager get() {
        return sInstance;
    }

    public static void reload(Context context) {
        if (context == null) {
            return;
        }
        sInstance.loadFromFile(new File(Environment.getExternalStorageDirectory(), FEATURE_PROP_FILE_NAME));
        sInstance.loadFromFile(new File(context.getFilesDir(), FEATURE_PROP_FILE_NAME));
        File external = context.getExternalFilesDir(null);
        if (external != null) {
            sInstance.loadFromFile(new File(external, FEATURE_PROP_FILE_NAME));
        }
    }

    private void loadFromFile(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        try (InputStream fis = new FileInputStream(file)) {
            mProperties.load(fis);
            Log.i(TAG, "Feature properties loaded from " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.w(TAG, "Failed to read feature properties from " + file.getAbsolutePath(), e);
        }
    }

    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = mProperties.getProperty(key, null);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    public double getDouble(String key, double defaultValue) {
        String value = mProperties.getProperty(key, null);
        return value != null ? Double.parseDouble(value) : defaultValue;
    }

    public float getFloat(String key, float defaultValue) {
        String value = mProperties.getProperty(key, null);
        return value != null ? Float.parseFloat(value) : defaultValue;
    }

    public int getInt(String key) {
        String value = mProperties.getProperty(key, null);
        return value != null ? Integer.parseInt(value) : 0;
    }

    public int getInt(String key, int defaultValue) {
        String value = mProperties.getProperty(key, null);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }

    public String getString(String key) {
        return mProperties.getProperty(key, null);
    }
}