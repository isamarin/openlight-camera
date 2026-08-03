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
import android.view.VelocityTracker;
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

    /**
     * Finger speed, in strip units per second, between which the zoom accelerates.
     *
     * Below the floor a step keeps costing its full travel, so a slow drag stays
     * exactly as precise as before — that is the mode you want when placing the
     * focal length by eye. Above the floor the cost shrinks, and a decisive sweep
     * crosses the range in a fraction of the strip. The curve is quadratic rather
     * than linear so the transition is not felt as a step: the first half of the
     * speed range barely accelerates at all.
     *
     * The strip is 768 units wide and reports around 60 events per second, so a
     * leisurely drag sits near 300 u/s and a brisk one near 2500.
     */
    private static final float ACCEL_SPEED_FLOOR = 350.0F;

    private static final float ACCEL_SPEED_CEILING = 2600.0F;

    private static final float ACCEL_MAX_GAIN = 3.5F;

    /**
     * A fast sweep asks for more detents than one callback should hand over at once —
     * fired together they read as a hop rather than a movement. Two go out inline so
     * slow work keeps its immediate response, and the rest is paid out over the frames
     * that follow, at most STEPS_PER_PUMP_TICK per 16 ms. Backlog is capped so the zoom
     * never keeps running noticeably after the finger has stopped.
     */
    private static final int STEPS_INLINE_LIMIT = 2;

    private static final int STEPS_PER_PUMP_TICK = 3;

    private static final long PUMP_TICK_MS = 16L;

    private static final int MAX_PENDING_STEPS = 18;

    /** Weight of the newest sample in the speed estimate; lower is smoother, laggier. */
    private static final float SPEED_SMOOTHING = 0.45F;

    /** Release faster than this coasts; slower than this stops dead under the finger. */
    private static final float MOMENTUM_MIN_VELOCITY = 900.0F;

    private static final float MOMENTUM_STOP_VELOCITY = 220.0F;

    /** Per-tick decay. 0.90 at 16 ms leaves about a third of the speed after 100 ms. */
    private static final float MOMENTUM_FRICTION_PER_TICK = 0.90F;

    private static final long MOMENTUM_TICK_MS = 16L;

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

    /** Speed of the finger right now, in strip units per second; 0 when not touching. */
    private volatile float mCurrentSpeed = 0.0F;

    /** Signed speed while the zoom coasts after release; 0 when at rest. */
    private volatile float mMomentumVelocity = 0.0F;

    /** Travel banked by the coast since its last emitted step. */
    private float mMomentumTravel = 0.0F;

    /** Detents owed to the listener, signed the way emitSteps reads them. */
    private volatile int mPendingSteps = 0;

    private VelocityTracker mVelocityTracker;

    /** Set when a touch landed only to halt a coast, so the tap it produces is swallowed. */
    private volatile boolean mTouchStoppedMomentum = false;

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

    /** Pays out deferred detents a few per frame, so a burst reads as motion, not a hop. */
    private final Runnable mStepPumpRunnable = new Runnable() {
        public void run() {
            int pending = TouchStrip.this.mPendingSteps;
            if (pending == 0) {
                return;
            }
            boolean left = pending > 0;
            int count = Math.min(Math.abs(pending), STEPS_PER_PUMP_TICK);
            TouchStrip.this.mPendingSteps = pending - (left ? count : -count);
            TouchStrip.this.notifySteps(left, count);
            if (TouchStrip.this.mPendingSteps != 0) {
                TouchStrip.this.mFlingProcessingHandler.postDelayed(this, PUMP_TICK_MS);
            }
        }
    };

    /** Drives the coast after release: decay the speed, bank the travel, emit whole steps. */
    private final Runnable mMomentumRunnable = new Runnable() {
        public void run() {
            float velocity = TouchStrip.this.mMomentumVelocity;
            if (Math.abs(velocity) < MOMENTUM_STOP_VELOCITY) {
                TouchStrip.this.stopMomentum();
                return;
            }
            TouchStrip.this.mMomentumTravel += Math.abs(velocity) * MOMENTUM_TICK_MS / 1000.0F;
            float perStep = TouchStrip.this.getScrollDistancePerStep()
                    / TouchStrip.this.gainForSpeed(Math.abs(velocity));
            int steps = (int) (TouchStrip.this.mMomentumTravel / perStep);
            if (steps > 0) {
                TouchStrip.this.mMomentumTravel -= steps * perStep;
                // Coasting right means the focal ran the way a leftward drag sends it.
                TouchStrip.this.emitSteps((velocity > 0.0F) ? -steps : steps);
            }
            TouchStrip.this.mMomentumVelocity = velocity * MOMENTUM_FRICTION_PER_TICK;
            TouchStrip.this.mFlingProcessingHandler.postDelayed(this, MOMENTUM_TICK_MS);
        }
    };

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
                if (TouchStrip.this.mCurrentSpeed >= THRESHOLD_FLING_VELOCITY) {
                    // Fast enough to be a deliberate fling; onFling jumps to a prime.
                    // The test used to be on the distance of a single callback, which
                    // threw away the travel of every quick swipe and left the zoom
                    // feeling the same however hard you moved.
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
                if (TouchStrip.this.mTouchStoppedMomentum) {
                    // The finger came down to catch a coasting zoom; that is a brake,
                    // not a request to jump to a prime.
                    TouchStrip.this.mTouchStoppedMomentum = false;
                    return true;
                }
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
        float perStep = getScrollDistancePerStep() / gainForSpeed(this.mCurrentSpeed);
        int steps = (int) (this.mScrollAccumulator / perStep);
        if (steps == 0) {
            return false;
        }
        this.mScrollAccumulator -= steps * perStep;
        logIt(TAG, "speed " + Math.round(this.mCurrentSpeed) + " u/s, gain "
                + gainForSpeed(this.mCurrentSpeed) + ", steps " + steps);
        emitSteps(steps);
        return true;
    }

    /**
     * Hands the listener a few detents now and defers the rest, so a quick sweep runs
     * the zoom instead of throwing it. Sign follows scroll distance, positive leftwards.
     */
    private void emitSteps(int paramInt) {
        if (paramInt == 0) {
            return;
        }
        int inline = Math.min(Math.abs(paramInt), STEPS_INLINE_LIMIT);
        notifySteps(paramInt > 0, inline);

        int deferred = Math.abs(paramInt) - inline;
        if (deferred <= 0) {
            return;
        }
        // A reversal cancels what is still owed rather than fighting it.
        if ((this.mPendingSteps > 0) != (paramInt > 0)) {
            this.mPendingSteps = 0;
        }
        int pending = Math.abs(this.mPendingSteps) + deferred;
        this.mPendingSteps = Math.min(pending, MAX_PENDING_STEPS) * ((paramInt > 0) ? 1 : -1);
        this.mFlingProcessingHandler.removeCallbacks(this.mStepPumpRunnable);
        this.mFlingProcessingHandler.postDelayed(this.mStepPumpRunnable, PUMP_TICK_MS);
    }

    private void notifySteps(boolean paramBoolean, int paramInt) {
        Event event = paramBoolean ? Event.SWIPE_LEFT : Event.SWIPE_RIGHT;
        for (int i = 0; i < paramInt; i++) {
            notifyEventListeners(event);
        }
    }

    /**
     * How much the zoom outruns the finger at a given speed. Flat at 1.0 below the
     * floor so slow work stays one step per fixed travel, then quadratic up to
     * ACCEL_MAX_GAIN — the same shape a pointer acceleration curve uses, and the
     * reason a hurried sweep covers ground while a careful one still lands on the
     * detent you aimed at.
     */
    private float gainForSpeed(float paramFloat) {
        if (paramFloat <= ACCEL_SPEED_FLOOR) {
            return 1.0F;
        }
        if (paramFloat >= ACCEL_SPEED_CEILING) {
            return ACCEL_MAX_GAIN;
        }
        float t = (paramFloat - ACCEL_SPEED_FLOOR) / (ACCEL_SPEED_CEILING - ACCEL_SPEED_FLOOR);
        return 1.0F + t * t * (ACCEL_MAX_GAIN - 1.0F);
    }

    /**
     * Keeps the finger's speed current. The gesture detector does not report it, so
     * the raw events are tapped on their way through processEvent.
     */
    private void trackVelocity(MotionEvent paramMotionEvent) {
        switch (paramMotionEvent.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                this.mTouchStoppedMomentum = (this.mMomentumVelocity != 0.0F || this.mPendingSteps != 0);
                stopMomentum();
                this.mPendingSteps = 0;
                this.mFlingProcessingHandler.removeCallbacks(this.mStepPumpRunnable);
                if (this.mVelocityTracker == null) {
                    this.mVelocityTracker = VelocityTracker.obtain();
                } else {
                    this.mVelocityTracker.clear();
                }
                this.mVelocityTracker.addMovement(paramMotionEvent);
                this.mCurrentSpeed = 0.0F;
                break;
            case MotionEvent.ACTION_MOVE:
                if (this.mVelocityTracker != null) {
                    this.mVelocityTracker.addMovement(paramMotionEvent);
                    this.mVelocityTracker.computeCurrentVelocity(1000);
                    float sample = Math.abs(this.mVelocityTracker.getXVelocity());
                    // Raw per-event velocity jitters enough to make the gain wobble
                    // within a single sweep, which is felt as unevenness.
                    this.mCurrentSpeed = this.mCurrentSpeed
                            + SPEED_SMOOTHING * (sample - this.mCurrentSpeed);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                float velocity = 0.0F;
                if (this.mVelocityTracker != null) {
                    this.mVelocityTracker.addMovement(paramMotionEvent);
                    this.mVelocityTracker.computeCurrentVelocity(1000);
                    velocity = this.mVelocityTracker.getXVelocity();
                    this.mVelocityTracker.recycle();
                    this.mVelocityTracker = null;
                }
                this.mCurrentSpeed = 0.0F;
                startMomentum(velocity);
                break;
        }
    }

    /**
     * Lets the zoom coast on after the finger leaves, decaying to a stop. Anything
     * slower than MOMENTUM_MIN_VELOCITY stops where it was put; anything above the
     * fling threshold belongs to onFling, which jumps to the next prime instead.
     */
    private void startMomentum(float paramFloat) {
        float speed = Math.abs(paramFloat);
        if (speed < MOMENTUM_MIN_VELOCITY || speed >= THRESHOLD_FLING_VELOCITY) {
            return;
        }
        this.mMomentumVelocity = paramFloat;
        this.mMomentumTravel = 0.0F;
        this.mFlingProcessingHandler.post(this.mMomentumRunnable);
    }

    private void stopMomentum() {
        this.mMomentumVelocity = 0.0F;
        this.mMomentumTravel = 0.0F;
        this.mFlingProcessingHandler.removeCallbacks(this.mMomentumRunnable);
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
            trackVelocity(paramMotionEvent);
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
