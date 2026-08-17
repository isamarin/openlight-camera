package openlight.astro.probe;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.widget.TextView;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Пробник: может ли обычное приложение снять полноценный шестнадцатимодульный кадр L16.
 *
 * Проверяются ровно две вещи, ради которых он и написан:
 *
 * 1. Заведётся ли захват из приложения, написанного с нуля, — то есть ляжет ли на карту
 *    .lri на полторы сотни мегабайт. Штатная камера стоит в /system/priv-app, наша сборка
 *    в /data/app и снимает; но наша сборка — перелицованное штатное приложение, а этот
 *    пробник родства с ним не имеет.
 * 2. Видны ли вендорные теги Light обычному приложению, и какие. Их список печатается,
 *    а не угадывается.
 *
 * Изображение через поверхности не приходит: у штатного приложения в сессии одна
 * поверхность превью, а .lri пишет сам ASIC в хранилище. Поэтому здесь нет ни
 * ImageReader, ни разбора кадров — только команда и наблюдение за каталогом.
 */
public class ProbeActivity extends Activity {

    private static final File DCIM = new File("/sdcard/DCIM/Camera");
    private static final FilenameFilter LRI = (dir, name) -> name.endsWith(".lri");

    private HandlerThread mThread;
    private Handler mHandler;
    private CameraDevice mCamera;
    private CameraCaptureSession mSession;
    private SurfaceTexture mTexture;
    private Surface mSurface;
    private int mLriBefore;

    private TextView mScreen;
    private final StringBuilder mShown = new StringBuilder();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        mScreen = new TextView(this);
        mScreen.setTextSize(11f);
        setContentView(mScreen);

        Probe.reset();
        mThread = new HandlerThread("probe");
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
        mHandler.post(this::start);
    }

    private void show(String line) {
        Probe.say(line);
        mShown.append(line).append('\n');
        runOnUiThread(() -> mScreen.setText(mShown.toString()));
    }

    private void start() {
        show("пробник запущен, приложение " + getPackageName());
        String[] lri = DCIM.list(LRI);
        mLriBefore = lri == null ? 0 : lri.length;
        show("кадров .lri на карте до съёмки: " + mLriBefore);

        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            String[] ids = manager.getCameraIdList();
            show("камер видно: " + ids.length + " " + Arrays.toString(ids));
            if (ids.length == 0) {
                show("ВЫВОД: камер нет, дальше идти некуда");
                return;
            }

            String id = ids[0];
            CameraCharacteristics chars = manager.getCameraCharacteristics(id);
            describe(chars);

            show("открываю камеру " + id);
            manager.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice camera) {
                    mCamera = camera;
                    show("камера открыта");
                    configure(chars);
                }
                @Override public void onDisconnected(CameraDevice camera) {
                    show("камера отсоединена");
                    camera.close();
                }
                @Override public void onError(CameraDevice camera, int error) {
                    show("ВЫВОД: камера не открылась, код " + error);
                    camera.close();
                }
            }, mHandler);
        } catch (CameraAccessException | SecurityException e) {
            Probe.fail("не удалось добраться до камеры", e);
            show("ВЫВОД: доступ к камере закрыт — " + e);
        }
    }

    /** Печатаем то, что камера сама о себе рассказывает: гадать про вендорные теги незачем. */
    private void describe(CameraCharacteristics chars) {
        List<String> vendor = new ArrayList<>();
        for (CaptureRequest.Key<?> k : chars.getAvailableCaptureRequestKeys()) {
            if (k.getName().startsWith("co.light")) {
                vendor.add(k.getName());
            }
        }
        show("вендорных ключей запроса видно: " + vendor.size() + " " + vendor);

        List<String> vendorResult = new ArrayList<>();
        for (CaptureResult.Key<?> k : chars.getAvailableCaptureResultKeys()) {
            if (k.getName().startsWith("co.light")) {
                vendorResult.add(k.getName());
            }
        }
        show("вендорных ключей результата видно: " + vendorResult.size() + " " + vendorResult);

        try {
            android.util.Range<Long> r =
                    chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            if (r != null) {
                show("выдержка по железу: " + r.getLower() + " … " + r.getUpper() + " нс");
            }
            android.util.Range<Integer> iso =
                    chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            if (iso != null) {
                show("ISO по железу: " + iso.getLower() + " … " + iso.getUpper());
            }
            Integer maxAnalog = chars.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY);
            if (maxAnalog != null) {
                show("предел аналогового усиления: ISO " + maxAnalog);
            }
        } catch (Throwable t) {
            Probe.fail("характеристики читаются не полностью", t);
        }
    }

    private void configure(CameraCharacteristics chars) {
        try {
            Size preview = new Size(1920, 1080);
            StreamConfigurationMap map =
                    chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
                if (sizes != null && sizes.length > 0) {
                    preview = sizes[0];
                }
            }
            show("поверхность превью " + preview.getWidth() + "x" + preview.getHeight());

            mTexture = new SurfaceTexture(0);
            mTexture.setDefaultBufferSize(preview.getWidth(), preview.getHeight());
            mSurface = new Surface(mTexture);

            mCamera.createCaptureSession(java.util.Collections.singletonList(mSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession session) {
                            mSession = session;
                            show("сессия собрана");
                            runPreviewThenCapture();
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession session) {
                            show("ВЫВОД: сессия не собралась");
                        }
                    }, mHandler);
        } catch (Throwable t) {
            Probe.fail("сессия не настроилась", t);
            show("ВЫВОД: сессия не настроилась — " + t);
        }
    }

    private void runPreviewThenCapture() {
        try {
            CaptureRequest.Builder preview =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            preview.addTarget(mSurface);
            mSession.setRepeatingRequest(preview.build(), null, mHandler);
            show("превью пошло");

            // Дать ASIC устояться: штатное приложение тоже снимает не в первый же кадр.
            mHandler.postDelayed(this::takeShot, 2500);
        } catch (Throwable t) {
            Probe.fail("превью не запустилось", t);
            show("ВЫВОД: превью не запустилось — " + t);
        }
    }

    /**
     * Перебор вендорных команд.
     *
     * Обычный STILL_CAPTURE кадра не даёт — значит захват шестнадцати модулей запускается
     * чем-то ещё. Известно, что Light гоняет нестандартные значения CONTROL_CAPTURE_INTENT
     * (11 — перекачка серии, 12 — шесть кадров, 13 — три) и держит вендорный ключ
     * co.light.stacked_capture_state. Проверяем по одному и смотрим на каталог: гадать
     * дешевле перебором, чем чтением смали дальше.
     */
    private static final int[] INTENTS = {2, 6, 7, 8, 9, 10, 11, 12, 13, 14};
    private int mStep = -1;

    private void takeShot() {
        mStep++;
        if (mStep >= INTENTS.length) {
            show("перебор закончен");
            verdict();
            return;
        }
        int intentValue = INTENTS[mStep];
        String[] before = DCIM.list(LRI);
        final int had = before == null ? 0 : before.length;

        try {
            CaptureRequest.Builder still =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            still.addTarget(mSurface);
            still.set(CaptureRequest.CONTROL_CAPTURE_INTENT, intentValue);

            CaptureRequest.Key<Byte> stacked =
                    VendorKeys.request("co.light.stacked_capture_state", Byte.class);
            if (stacked != null) {
                still.set(stacked, (byte) 1);
            }

            show("проба " + (mStep + 1) + "/" + INTENTS.length
                    + ": CONTROL_CAPTURE_INTENT=" + intentValue + ", stacked_capture_state=1");

            mSession.capture(still.build(), new CameraCaptureSession.CaptureCallback() {
                @Override public void onCaptureCompleted(CameraCaptureSession session,
                                                         CaptureRequest request,
                                                         TotalCaptureResult result) {
                    mHandler.postDelayed(() -> check(intentValue, had), 12000);
                }
                @Override public void onCaptureFailed(CameraCaptureSession session,
                                                      CaptureRequest request,
                                                      android.hardware.camera2.CaptureFailure f) {
                    show("  отклонено, причина " + f.getReason());
                    mHandler.postDelayed(() -> check(intentValue, had), 1500);
                }
            }, mHandler);
        } catch (Throwable t) {
            show("  не принято: " + t);
            mHandler.postDelayed(this::takeShot, 800);
        }
    }

    private void check(int intentValue, int had) {
        String[] now = DCIM.list(LRI);
        int after = now == null ? 0 : now.length;
        if (after > had) {
            show("  ПОЛУЧИЛОСЬ на CONTROL_CAPTURE_INTENT=" + intentValue
                    + ": кадров стало " + after);
        } else {
            show("  пусто");
        }
        mHandler.post(this::takeShot);
    }

    private static Object safe(TotalCaptureResult result, CaptureResult.Key<?> key) {
        try {
            return result.get(key);
        } catch (Throwable t) {
            return "(нет в результате)";
        }
    }

    private void verdict() {
        String[] lri = DCIM.list(LRI);
        int after = lri == null ? 0 : lri.length;
        show("кадров .lri после съёмки: " + after + " (было " + mLriBefore + ")");
        if (after > mLriBefore) {
            long biggest = 0;
            String name = "";
            for (String n : lri) {
                File f = new File(DCIM, n);
                if (f.lastModified() > biggest) {
                    biggest = f.lastModified();
                    name = n + ", " + f.length() + " Б";
                }
            }
            show("ВЫВОД: получилось. Новый кадр: " + name);
        } else {
            show("ВЫВОД: команда прошла, но .lri не появился — захват принадлежит не Camera2");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mSession != null) mSession.close();
        if (mCamera != null) mCamera.close();
        if (mSurface != null) mSurface.release();
        if (mTexture != null) mTexture.release();
        if (mThread != null) mThread.quitSafely();
    }
}
