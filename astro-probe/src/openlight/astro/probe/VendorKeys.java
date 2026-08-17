package openlight.astro.probe;

import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.util.Log;

import java.lang.reflect.Constructor;

/**
 * Доступ к вендорным тегам Light через рефлексию.
 *
 * Camera2 умеет вендорные теги, но конструктор {@code CaptureRequest.Key(String, Class)}
 * скрыт от приложений. Штатное приложение камеры обходит это ровно так же — см.
 * {@code openlight/co/lightsdk/camera2/KeyMapperInternal}. На Android 6 запрета на
 * рефлексию к скрытым членам ещё нет, так что приём работает из любого приложения.
 *
 * Именно это и проверяет пробник: нужны ли для доступа к шестнадцати модулям привилегии
 * системного раздела, или хватает обычного APK с разрешением CAMERA.
 */
final class VendorKeys {
    private static final String TAG = Probe.TAG;

    /** Значения {@code CONTROL_CAPTURE_INTENT}, которых нет в Android: ими Light командует ASIC. */
    static final int INTENT_BURST_TRANSFER = 11;
    static final int INTENT_BURST_SIX_SHOT = 12;
    static final int INTENT_BURST_THREE_SHOT = 13;

    private VendorKeys() {}

    @SuppressWarnings("unchecked")
    static <T> CaptureRequest.Key<T> request(String name, Class<T> type) {
        try {
            Constructor<?> c = CaptureRequest.Key.class.getConstructor(String.class, Class.class);
            return (CaptureRequest.Key<T>) c.newInstance(name, type);
        } catch (Throwable t) {
            Log.e(TAG, "не удалось собрать ключ запроса " + name, t);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    static <T> CaptureResult.Key<T> result(String name, Class<T> type) {
        try {
            Constructor<?> c = CaptureResult.Key.class.getConstructor(String.class, Class.class);
            return (CaptureResult.Key<T>) c.newInstance(name, type);
        } catch (Throwable t) {
            Log.e(TAG, "не удалось собрать ключ результата " + name, t);
            return null;
        }
    }
}
