package openlight.co.camera.managers.capture;

import android.hardware.camera2.CaptureRequest;
import android.os.Environment;
import java.io.File;
import openlight.co.camera.enums.BurstMode;
import openlight.co.lib.content.CamPrefsFactory;
import openlight.co.lib.content.Prefs;

public class CaptureBurstManager {
    private static final String BURST_DISABLE_FILE = "disable_burst.prop";
    private static final int BURST_SIX_SHOT_CAPTURE_INTENT = 12;
    private static final int BURST_THREE_SHOT_CAPTURE_INTENT = 13;
    private static final int BURST_TRANSFER_CAPTURE_INTENT = 11;

    private static CaptureBurstManager sInstance;

    private final Prefs mCamPref = CamPrefsFactory.get();
    private final boolean mIsBurstDisabled =
            new File(Environment.getExternalStorageDirectory(), BURST_DISABLE_FILE).exists();
    private boolean mIsFetchRequest = true;
    private int mBurstOrientation = 0;

    public static synchronized CaptureBurstManager get() {
        synchronized (CaptureBurstManager.class) {
            if (sInstance == null) {
                sInstance = new CaptureBurstManager();
            }
            return sInstance;
        }
    }

    private CaptureBurstManager() {
        if (mIsBurstDisabled) {
            mCamPref.removeValue("burst_mode");
        }
    }

    public int getPendingUserCaptureCount() {
        BurstMode mode = BurstMode.getModeByPrefsKey(mCamPref.getStringValue("burst_mode"));
        switch (mode) {
            case CAPTURE_SIX:
                return 6;
            case CAPTURE_THREE:
                return 3;
            default:
                return 0;
        }
    }

    void setCaptureRequestForBurst(CaptureRequest.Builder builder, int burstIndex) {
        BurstMode mode = BurstMode.getModeByPrefsKey(mCamPref.getStringValue("burst_mode"));
        mIsFetchRequest = true;
        int captureIntent;
        switch (mode) {
            case CAPTURE_SIX:
                if (burstIndex == 6) {
                    mIsFetchRequest = false;
                    captureIntent = BURST_SIX_SHOT_CAPTURE_INTENT;
                } else {
                    captureIntent = BURST_TRANSFER_CAPTURE_INTENT;
                }
                break;
            case CAPTURE_THREE:
                if (burstIndex == 3) {
                    mIsFetchRequest = false;
                    captureIntent = BURST_THREE_SHOT_CAPTURE_INTENT;
                } else {
                    captureIntent = BURST_TRANSFER_CAPTURE_INTENT;
                }
                break;
            default:
                captureIntent = BURST_TRANSFER_CAPTURE_INTENT;
                break;
        }
        builder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, captureIntent);
    }

    public boolean getIfFetchRequest() {
        return mIsFetchRequest;
    }

    public void setBurstOrientation(int orientation) {
        mBurstOrientation = orientation;
    }

    public int getBurstOrientation() {
        return mBurstOrientation;
    }
}