package openlight.co.touchstrip;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.view.GestureDetectorCompat;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import java.util.ArrayList;

public class TouchStrip {
    private static final boolean IS_ENG_BUILD = Build.TYPE.equalsIgnoreCase("eng");

    public static final String SHARED_PREF_TOUCHSTRIP_SETTING = "device_touchstrip_setting";

    public static final String SHARED_PREF_TOUCHSTRIP_SETTING_DEFAULT = "off";

    public static final String SHARED_PREF_TOUCHSTRIP_SETTING_OFF = "off";

    /** How much strip travel one zoom step costs; see SCROLL_DISTANCE_PER_STEP. */
    public static final String SHARED_PREF_TOUCHSTRIP_SPEED_SETTING = "device_touchstrip_speed_setting";

    public static final String SPEED_SLOW = "slow";

    public static final String SPEED_DEFAULT = "normal";

    public static final String SPEED_FAST = "fast";

    private static final String TAG = "TouchStrip";

    private static final int TAP_CENTER_OFFSET_RANGE = 100;

    private static final long THRESHOLD_CUMULATIVE_DISTANCE_IN_DURATION = 350L;

    private static final int THRESHOLD_FLING_VELOCITY = 6000;

    private static final long THRESHOLD_NUMBER_OF_EVENTS_IN_DURATION = 8L;

    private static final int THRESHOLD_OFFSET_FROM_CENTER_FOR_TAP_LR = 100;

    private static final int THRESHOLD_SCROLL_MAX_DISTANCE = 150;

    private static final int THRESHOLD_SCROLL_MIN_DISTANCE = 5;

    /**
     * Strip travel, in device units, that makes up one zoom step.
     *
     * The original code emitted a swipe per scroll callback whose delta cleared
     * THRESHOLD_SCROLL_MIN_DISTANCE, so the zoom tracked how many events the
     * detector happened to fire rather than how far the finger actually moved —
     * a slow drag produced a burst of steps. Accumulating distance instead makes
     * one step cost a fixed amount of travel. The strip reports 0..768 and the
     * zoom wheel has ~52 detents, so ~15 units per step spans the whole range in
     * a single pass.
     */
    private static final float SCROLL_DISTANCE_PER_STEP = 15.0F;

    private static final int MAX_STEPS_PER_SCROLL_EVENT = 4;

    private static final long TIME_DURATION_TO_COUNT_EVENTS = 400L;

    private static final int TOUCH_STRIP_LENGTH = 800;

    private static final int TOUCH_STRIP_MIDPOINT_EVENT_X = 400;

    private static final boolean VERBOSE_LOG = false;

    private static TouchStrip sInstance;

    private Application mApplication;

    private SharedPreferences mCameraSettingsSharedPreferences;

    private volatile Event mCurrentEventForTimeDurationMatching = Event.UNKNOWN;

    private volatile int mCurrentScrollEventConsecutiveOccurrences = 0;

    private volatile int mCurrentScrollEventsCumulativeDistance = 0;

    /** Strip travel banked since the last emitted zoom step. */
    private volatile float mScrollAccumulator = 0.0F;

    private GestureDetectorCompat mDetector;

    // $FF: synthetic method
    static boolean access$902(TouchStrip var0, boolean var1) {
        var0.mTouchStripCurrentlyLongPressedLeft = var1;
        return var1;
    }

    static boolean access$1002(TouchStrip var0, boolean var1) {
        var0.mTouchStripCurrentlyLongPressedRight = var1;
        return var1;
    }

    private final Runnable mFlingGeneratorRunnable = new Runnable() {
        //final TouchStrip this$0;

        public void run() {
            // Light promoted a sustained scroll into a fling, which made the listener
            // jump to the next prime focal length — so every swipe snapped between
            // primes instead of scrubbing the range. (Their guard was also broken:
            // both halves tested mCurrentScrollEventConsecutiveOccurrences, so the
            // 350-unit distance check never applied and >8 events was enough.)
            // A swipe now steps the zoom smoothly; jumping to a prime is what a
            // deliberate fling (onFling, velocity over THRESHOLD_FLING_VELOCITY) and
            // a tap are for.
            TouchStrip.this.stopFlingEventDetection();
        }
    };

    private final Handler mFlingProcessingHandler = new Handler(Looper.getMainLooper());

    private boolean mIsTouchStripDisabled = true;

    private final ArrayList<OnTouchStripEventListener> mOnTouchStripEventListeners = new ArrayList<OnTouchStripEventListener>();

    private final SharedPreferences.OnSharedPreferenceChangeListener mOnTouchStripPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        //final TouchStrip this$0;

        public void onSharedPreferenceChanged(SharedPreferences param1SharedPreferences, String param1String) {
            if (param1String.equals("device_touchstrip_setting")) {
                TouchStrip.this.updateTouchStripStatus(TouchStrip.this.mApplication);
                TouchStrip.this.logIt(TouchStrip.TAG, "Touchstrip shared preferences have been updated");
            }
        }
    };

    private boolean mTouchStripCurrentlyLongPressedLeft = false;

    private boolean mTouchStripCurrentlyLongPressedRight = false;

    private boolean detectLongPressOrProcessEvent(MotionEvent paramMotionEvent) {
        if (paramMotionEvent.getAction() == 1) {
            if (this.mTouchStripCurrentlyLongPressedLeft) {
                logIt(TAG, "onLeftLongPressEnd " + paramMotionEvent.toString());
                this.mTouchStripCurrentlyLongPressedLeft = false;
                notifyEventListeners(Event.LONG_PRESS_END);
                return true;
            }
            if (this.mTouchStripCurrentlyLongPressedRight) {
                this.mTouchStripCurrentlyLongPressedRight = false;
                logIt(TAG, "onRightLongPressEnd " + paramMotionEvent.toString());
                notifyEventListeners(Event.LONG_PRESS_END);
                return true;
            }
        }
        return this.mDetector.onTouchEvent(paramMotionEvent);
    }

    public static synchronized TouchStrip get(Application application) {
        if (sInstance == null) {
            sInstance = new TouchStrip();
        }
        sInstance.updateTouchStripStatus(application);
        sInstance.mApplication = application;
        return sInstance;
    }

    private SharedPreferences getSharedPreferences(Context paramContext) {
        if (this.mCameraSettingsSharedPreferences == null) {
            this.mCameraSettingsSharedPreferences = paramContext
                    .getSharedPreferences(paramContext.getString(R.string.camera_mode_preference_key), 0);
            this.mCameraSettingsSharedPreferences
                    .registerOnSharedPreferenceChangeListener(this.mOnTouchStripPreferenceChangeListener);
        }
        return this.mCameraSettingsSharedPreferences;
    }

    private GestureDetectorCompat initTouchDetector() {
        GestureDetector.OnGestureListener onGestureListener = new GestureDetector.OnGestureListener() {
            //final TouchStrip this$0;

            public boolean onDown(MotionEvent param1MotionEvent) {
                TouchStrip touchStrip = TouchStrip.this;
                touchStrip.logIt(TouchStrip.TAG, "onDown " + param1MotionEvent.toString());
                // Travel banked by the previous gesture must not spill into this one.
                touchStrip.mScrollAccumulator = 0.0F;
                return true;
            }

            public boolean onFling(MotionEvent param1MotionEvent1, MotionEvent param1MotionEvent2, float param1Float1,
                    float param1Float2) {
                if (param1MotionEvent1 == null || param1MotionEvent2 == null) {
                    TouchStrip.this.logIt(TouchStrip.TAG, "onFling: one of the MotionEvents null, strange");
                    return false;
                }
                TouchStrip touchStrip = TouchStrip.this;
                touchStrip.logIt(TouchStrip.TAG, "onFling: " + param1Float1 + " " + param1MotionEvent1.toString() + " " + param1MotionEvent2.toString());
                if (param1Float1 > 6000.0F) {
                    TouchStrip.this.stopFlingEventDetection();
                    TouchStrip.this.notifyEventListeners(TouchStrip.Event.FLING_RIGHT);
                    return true;
                }
                if (param1Float1 < -6000.0F) {
                    TouchStrip.this.stopFlingEventDetection();
                    TouchStrip.this.notifyEventListeners(TouchStrip.Event.FLING_LEFT);
                    return true;
                }
                return false;
            }

            public void onLongPress(MotionEvent param1MotionEvent) {
                TouchStrip.this.stopFlingEventDetection();
                if (param1MotionEvent.getX() < 400.0F) {
                    TouchStrip touchStrip = TouchStrip.this;
                    touchStrip.logIt(TouchStrip.TAG, "onLeftLongPressStart " + param1MotionEvent.toString());
                    TouchStrip.access$902(TouchStrip.this, true);
                    TouchStrip.this.notifyEventListeners(TouchStrip.Event.LONG_PRESS_START_LEFT);
                } else {
                    TouchStrip touchStrip = TouchStrip.this;
                    touchStrip.logIt(TouchStrip.TAG, "onRightLongPressStart " + param1MotionEvent.toString());
                    TouchStrip.access$1002(TouchStrip.this, true);
                    TouchStrip.this.notifyEventListeners(TouchStrip.Event.LONG_PRESS_START_RIGHT);
                }
            }

            public boolean onScroll(MotionEvent param1MotionEvent1, MotionEvent param1MotionEvent2, float param1Float1,
                    float param1Float2) {
                TouchStrip.this.processScrollEventToDetectSensitiveFling(param1Float1);
                if (param1Float1 <= -150.0F || param1Float1 >= 150.0F) {
                    // Jump this large is a fling; leave it to onFling.
                    return false;
                }
                return TouchStrip.this.emitScrollSteps(param1Float1);
            }

            public void onShowPress(MotionEvent param1MotionEvent) {
                TouchStrip touchStrip = TouchStrip.this;
                touchStrip.logIt(TouchStrip.TAG, "onShowPress " + param1MotionEvent.toString());
            }

            public boolean onSingleTapUp(MotionEvent param1MotionEvent) {
                boolean bool;
                TouchStrip touchStrip = TouchStrip.this;
                touchStrip.logIt(TouchStrip.TAG, "onSingleTapUp " + param1MotionEvent.toString());
                TouchStrip.this.stopFlingEventDetection();
                if (param1MotionEvent.getX() < 500.0F && param1MotionEvent.getX() > 300.0F) {
                    TouchStrip.this.notifyEventListeners(TouchStrip.Event.TAP_CENTER);
                    bool = true;
                } else {
                    bool = false;
                }
                if (param1MotionEvent.getX() < 100.0F) {
                    TouchStrip.this.notifyEventListeners(TouchStrip.Event.TAP_LEFT);
                    bool = true;
                }
                if (param1MotionEvent.getX() > 700.0F) {
                    TouchStrip.this.notifyEventListeners(TouchStrip.Event.TAP_RIGHT);
                    bool = true;
                }
                return bool;
            }
        };
        return new GestureDetectorCompat((Context) this.mApplication, onGestureListener);
    }

    private void logIt(String paramString1, String paramString2) {
        if (IS_ENG_BUILD)
            Log.d(paramString1, paramString2);
    }

    private void logIt(String paramString1, String paramString2, Exception paramException) {
        if (IS_ENG_BUILD)
            Log.e(paramString1, paramString2, paramException);
    }

    private void notifyEventListeners(Event paramEvent) {
        for (OnTouchStripEventListener onTouchStripEventListener : new ArrayList<OnTouchStripEventListener>(this.mOnTouchStripEventListeners)) {
            try {
                onTouchStripEventListener.onEvent(paramEvent);
            } catch (Exception exception) {
                logIt(TAG, "Exception in one of the listeners, ignoring", exception);
            }
        }
    }

    private void processScrollEventToDetectSensitiveFling(float paramFloat) {
        Event event;
        if (paramFloat < -5.0F) {
            event = Event.SWIPE_RIGHT;
        } else if (paramFloat > 5.0F) {
            event = Event.SWIPE_LEFT;
        } else {
            return;
        }
        if (this.mCurrentEventForTimeDurationMatching == Event.UNKNOWN
                || this.mCurrentEventForTimeDurationMatching != event) {
            stopFlingEventDetection();
            startFlingEventDetection(event);
        }
        if (this.mCurrentEventForTimeDurationMatching == event) {
            this.mCurrentScrollEventConsecutiveOccurrences++;
            this.mCurrentScrollEventsCumulativeDistance = (int) (this.mCurrentScrollEventsCumulativeDistance
                    + Math.abs(paramFloat));
        }
    }

    /**
     * Turns raw scroll distance into zoom steps at a fixed cost in strip travel,
     * so the zoom follows how far the finger moved rather than how many scroll
     * callbacks the detector produced.
     */
    private boolean emitScrollSteps(float paramFloat) {
        if (paramFloat > -THRESHOLD_SCROLL_MIN_DISTANCE && paramFloat < THRESHOLD_SCROLL_MIN_DISTANCE) {
            return false;
        }
        this.mScrollAccumulator += paramFloat;
        float perStep = getScrollDistancePerStep();
        int steps = (int) (this.mScrollAccumulator / perStep);
        if (steps == 0) {
            return false;
        }
        this.mScrollAccumulator -= steps * perStep;

        Event event = (steps > 0) ? Event.SWIPE_LEFT : Event.SWIPE_RIGHT;
        int count = Math.min(Math.abs(steps), MAX_STEPS_PER_SCROLL_EVENT);
        for (int i = 0; i < count; i++) {
            notifyEventListeners(event);
        }
        return true;
    }

    /** Strip travel per zoom step, widened or narrowed by the user's speed setting. */
    private float getScrollDistancePerStep() {
        String speed = SPEED_DEFAULT;
        if (this.mApplication != null) {
            speed = getSharedPreferences((Context) this.mApplication)
                    .getString(SHARED_PREF_TOUCHSTRIP_SPEED_SETTING, SPEED_DEFAULT);
        }
        if (SPEED_SLOW.equals(speed)) {
            return SCROLL_DISTANCE_PER_STEP * 2.0F;
        }
        if (SPEED_FAST.equals(speed)) {
            return SCROLL_DISTANCE_PER_STEP / 2.0F;
        }
        return SCROLL_DISTANCE_PER_STEP;
    }

    private void startFlingEventDetection(Event paramEvent) {
        this.mCurrentEventForTimeDurationMatching = paramEvent;
        this.mFlingProcessingHandler.postDelayed(this.mFlingGeneratorRunnable, 400L);
    }

    private void stopFlingEventDetection() {
        this.mCurrentScrollEventConsecutiveOccurrences = 0;
        this.mCurrentScrollEventsCumulativeDistance = 0;
        this.mCurrentEventForTimeDurationMatching = Event.UNKNOWN;
        this.mFlingProcessingHandler.removeCallbacks(this.mFlingGeneratorRunnable);
    }

    private void updateTouchStripStatus(Application paramApplication) {
        this.mIsTouchStripDisabled = "off".equals(getSharedPreferences((Context) paramApplication).getString("device_touchstrip_setting", "off"));
        Log.i(TAG, "TouchStrip Disabled: " + this.mIsTouchStripDisabled);
        if (!this.mIsTouchStripDisabled)
            this.mDetector = initTouchDetector();
    }

    public boolean processEvent(MotionEvent paramMotionEvent) {
        if (!this.mIsTouchStripDisabled && paramMotionEvent.getSource() == 1048584) {
            paramMotionEvent.setSource(2);
            return detectLongPressOrProcessEvent(paramMotionEvent);
        }
        return false;
    }

    public void registerEventListener(OnTouchStripEventListener paramOnTouchStripEventListener) {
        this.mOnTouchStripEventListeners.add(paramOnTouchStripEventListener);
    }

    public void unregisterEventListener(OnTouchStripEventListener paramOnTouchStripEventListener) {
        this.mOnTouchStripEventListeners.remove(paramOnTouchStripEventListener);
    }

    public enum Event {
        FLING_LEFT,
        FLING_RIGHT,
        LONG_PRESS_END,
        LONG_PRESS_START_LEFT,
        LONG_PRESS_START_RIGHT,
        SWIPE_LEFT,
        SWIPE_RIGHT,
        TAP_CENTER,
        TAP_LEFT,
        TAP_RIGHT,
        UNKNOWN;
    }

    public static abstract class OnTouchStripEventListener {
        public abstract void onEvent(TouchStrip.Event param1Event);
    }
}
