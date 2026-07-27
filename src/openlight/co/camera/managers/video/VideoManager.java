package openlight.co.camera.managers.video;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.media.CamcorderProfile;
import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Size;
import android.view.Surface;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import openlight.co.camera.CameraApp;
import openlight.co.camera.managers.CameraManager;
import openlight.co.camera.managers.focus.FocusManager;
import openlight.co.camera.metrics.CameraMetrics;
import openlight.co.camera.metrics.Metrics;
import openlight.co.camera.enums.VideoQualityMode;
import openlight.co.camera.utils.MediaFileManager;
import openlight.co.camera.view.rotate.OrientationsController;
import openlight.co.lib.utils.FeatureManager;
import openlight.co.lib.utils.LogUtil;
import openlight.co.lib.utils.Utils;

public class VideoManager extends CameraManager {
    private static final String TAG = "VideoManager";

    private static final VideoManager sVideoManager;

    static {
        sVideoManager = new VideoManager();
    }

    private final Metrics mCameraMetrics;
    private volatile State mCurrentState;
    private String mCurrentVideoAbsolutePath;
    private final MediaRecorder.OnErrorListener mErrorListener;
    private final MediaRecorder.OnInfoListener mInfoListener;
    private final MediaFileManager mMediaFileMgr;
    private MediaRecorder mMediaRecorder;
    private Surface mRecordingSurface;
    private OnStatusListener mStatusListener;
    private final File mThumbnailsDir;
    private volatile boolean mUseSuffixFileName;
    private int mVideoQualityProfile;

    /** h264 ceiling from /system/etc/media_profiles.xml on the L16. */
    private static final int MAX_VIDEO_BITRATE = 42000000;

    private int mVideoWidth;
    private int mVideoHeight;
    private int mVideoBitRate;
    private int mVideoFrameRate;
    private long mRecordingStartedAt;

    private VideoManager() {
        super();
        mVideoQualityProfile = FeatureManager.get().getInt("video.quality", 6);
        mCameraMetrics = Metrics.get();
        mMediaFileMgr = MediaFileManager.get();
        mCurrentState = State.NOT_INITIALIZED;
        mUseSuffixFileName = false;
        mThumbnailsDir = Utils.videoThumbnailsDir();
        mErrorListener = (mr, what, extra) -> {
            LogUtil.e(TAG, "Media recorder onError: " + what + " - " + extra);
            restartCamera();
            mCameraMetrics.add("event_media_recorder_error",
                    CameraMetrics.createPropertiesForMediaRecorderError(what, extra));
        };
        mInfoListener = (mr, what, extra) -> {
            LogUtil.i(TAG, "Media recorder onInfo: " + what + " - " + extra);
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED
                    || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                setCurrentState(State.PREVIEW);
                stopRecording();
                notifyStatusError(ErrorType.MAX_FILE_SIZE_REACHED);
            }
            mCameraMetrics.add("event_media_recorder_info",
                    CameraMetrics.createPropertiesForMediaRecorderInfo(what, extra));
        };
    }

    public static VideoManager get() {
        return sVideoManager;
    }

    public boolean canGotoGallery() {
        return mCurrentState == State.PREVIEW;
    }

    @Override
    public boolean closeCamera() {
        LogUtil.d(TAG, "in close camera");
        releaseMediaRecorder();
        return super.closeCamera();
    }

    public void continueRecording() {
        try {
            setUseSuffixFileName(true);
            startRecording();
        } catch (Exception e) {
            LogUtil.e(TAG, "Fail to continue recording.", e);
            Metrics.get().add("event_media_recorder_start_failed");
            notifyStatusError(ErrorType.VIDEO_RECORD_START_FAILED);
            restartCamera();
        }
    }

    @Override
    public void createCameraSession(SurfaceTexture surfaceTexture, Size size) {
        if (mCameraDevice == null) {
            LogUtil.e(TAG, "Cannot create video session: camera device is null");
            Metrics.get().add("event_camera_session_error");
            restartCamera();
            return;
        }
        try {
            setCurrentState(State.PREVIEW);
            mSurfaceList.clear();
            setupMediaRecorderAndSurface();
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mPreviewRequestBuilder.addTarget(mRecordingSurface);
            mSurfaceList.add(mRecordingSurface);
            createCameraPreviewSessionLocked(surfaceTexture, size);
        } catch (IOException | CameraAccessException e) {
            LogUtil.e(TAG, "Exception when creating preview session using TEMPLATE_RECORD", e);
            Metrics.get().add("event_camera_session_error");
            restartCamera();
        }
    }

    @Override
    public String getBackgroundThreadName() {
        return "VideoBackground";
    }

    public boolean isRecording() {
        return mCurrentState.isRecording();
    }

    public void setCurrentState(State state) {
        if (state != mCurrentState) {
            mCurrentState = state;
            if ((state == State.PREVIEW || state == State.RECORDING) && mStatusListener != null) {
                mStatusListener.onRecordStatusChange(state);
            }
        }
    }

    public void setStatusListener(OnStatusListener listener) {
        mStatusListener = listener;
    }

    public void setUseSuffixFileName(boolean useSuffix) {
        mUseSuffixFileName = useSuffix;
    }

    public void startRecording() {
        if (mMediaRecorder == null) {
            return;
        }
        try {
            LogUtil.d(TAG, "Start recording");
            setCurrentState(State.RECORDING);
            resetMediaRecorder();
            prepareMediaRecorder();
            mMediaRecorder.start();
            mRecordingStartedAt = SystemClock.elapsedRealtime();
        } catch (IOException | IllegalStateException e) {
            LogUtil.e(TAG, "Fail to start recording.", e);
            Metrics.get().add("event_media_recorder_start_failed");
            notifyStatusError(ErrorType.VIDEO_RECORD_START_FAILED);
            restartCamera();
        }
    }

    public void stopRecording() {
        LogUtil.d(TAG, "Stop Recording");
        if (mMediaRecorder == null) {
            LogUtil.w(TAG, "Stop recording requested but media recorder is null");
            return;
        }
        try {
            setCurrentState(State.PREVIEW);
            if (mStatusListener != null) {
                mStatusListener.onStopRecording();
            }
            mMediaRecorder.stop();
            if (mStatusListener != null) {
                mStatusListener.onMediaSaveComplete(mCurrentVideoAbsolutePath);
            }
            File videoFile = new File(mCurrentVideoAbsolutePath);
            logRecordingMetrics(videoFile);
            if (videoFile.exists() && videoFile.length() >= 0x2800) {
                MediaScannerConnection.scanFile(
                        CameraApp.get(),
                        new String[]{mCurrentVideoAbsolutePath},
                        new String[]{"video/mp4"},
                        this::createVideoThumbnail);
            } else {
                LogUtil.e(TAG, "Invalid MP4, file not found or size less than minimum");
                Metrics.get().add("event_media_recorder_invalid_size");
            }
            setUseSuffixFileName(false);
        } catch (RuntimeException e) {
            LogUtil.e(TAG, "Fail to stop recording.", e);
            Metrics.get().add("event_media_recorder_stop_failed");
            notifyStatusError(ErrorType.VIDEO_RECORD_STOP_FAILED);
            restartCamera();
        }
    }

    private void notifyStatusError(ErrorType errorType) {
        if (mStatusListener != null) {
            mStatusListener.onError(errorType);
        }
    }

    private void createVideoThumbnail(String path, Uri uri) {
        CameraApp context = CameraApp.get();
        String fileName = new File(path).getName();
        String thumbName = Utils.thumbnailNameFor(fileName);
        File destFile = new File(mThumbnailsDir, thumbName);

        if (destFile.exists()) {
            LogUtil.d(TAG, "Found existing thumbnail file for " + fileName + ", deleting it.");
            destFile.delete();
        }

        ContentResolver cr = context.getContentResolver();
        long videoId = ContentUris.parseId(uri);
        BitmapFactory.Options options = new BitmapFactory.Options();
        Bitmap thumb = MediaStore.Video.Thumbnails.getThumbnail(cr, videoId, MediaStore.Video.Thumbnails.MINI_KIND, options);

        if (thumb == null) {
            LogUtil.w(TAG, "No thumbnail for " + fileName);
            return;
        }

        Cursor cursor = cr.query(
                MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI,
                new String[]{"_id", "_data"},
                "video_id=?",
                new String[]{Long.toString(videoId)},
                null);

        if (cursor == null) {
            return;
        }

        try {
            if (cursor.moveToFirst()) {
                long thumbId = cursor.getLong(0);
                File thumbFile = new File(cursor.getString(1));
                if (thumbFile.exists() && thumbFile.renameTo(destFile)) {
                    Uri thumbUri = ContentUris.withAppendedId(
                            MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI, thumbId);
                    ContentValues cv = new ContentValues();
                    cv.put("_data", destFile.getAbsolutePath());
                    cr.update(thumbUri, cv, null, null);
                    MediaScannerConnection.scanFile(
                            context,
                            new String[]{destFile.getAbsolutePath()},
                            new String[]{"image/jpeg"},
                            (p, u) -> LogUtil.d(TAG, "scanned video thumbnail " + p));
                } else {
                    LogUtil.d(TAG, "Thumbnail file not found or failed to rename " + fileName);
                }
            } else {
                LogUtil.d(TAG, "Not thumbnail media store row " + fileName);
            }
        } finally {
            cursor.close();
        }
    }

    private String getVideoFileAbsolutePath() {
        if (mCurrentState == State.RECORDING) {
            mMediaFileMgr.deleteTempVideoFile();
            if (mUseSuffixFileName) {
                return mMediaFileMgr.getVideoFilePathWithSuffix();
            } else {
                mMediaFileMgr.resetVideoSuffixNumber();
                return mMediaFileMgr.getVideoFilePath();
            }
        }
        return mMediaFileMgr.getVideoTempFilePath();
    }

    private void prepareMediaRecorder() throws IOException {
        LogUtil.d(TAG, "prepareMediaRecorder");
        mMediaRecorder.setOnErrorListener(mErrorListener);
        mMediaRecorder.setOnInfoListener(mInfoListener);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setInputSurface(mRecordingSurface);
        mVideoQualityProfile = VideoQualityMode.valueOf(mCamPref.getStringValue("quality_profile"))
                .getQualityProfile();
        CamcorderProfile profile = CamcorderProfile.get(mVideoQualityProfile);
        mMediaRecorder.setProfile(profile);
        applyVideoOverrides(profile);
        mCurrentVideoAbsolutePath = getVideoFileAbsolutePath();
        mMediaRecorder.setOutputFile(mCurrentVideoAbsolutePath);
        mMediaRecorder.setOrientationHint(
                OrientationsController.get().getConfig().getOrientationHint());
        mMediaRecorder.prepare();
        LogUtil.d(TAG, "media recorder prepared");
    }

    /**
     * CamcorderProfile on the L16 only offers 480p/720p/1080p/2160p, so anything in
     * between has to be set on top of a profile. The size must be one the camera
     * actually offers (see CameraInfo's output size list) — MediaRecorder.prepare()
     * fails on anything else and recording never starts.
     */
    private void applyVideoOverrides(CamcorderProfile profile) {
        mVideoWidth = profile.videoFrameWidth;
        mVideoHeight = profile.videoFrameHeight;
        mVideoBitRate = profile.videoBitRate;
        mVideoFrameRate = profile.videoFrameRate;

        String requested = FeatureManager.get().getString("video.size");
        if (requested != null && !requested.trim().isEmpty()) {
            Size size = parseVideoSize(requested.trim());
            if (size == null) {
                LogUtil.w(TAG, "Ignoring malformed video.size: " + requested);
                mCameraMetrics.add("event_video_size_override_invalid");
            } else {
                // Keep bits-per-pixel roughly constant with the profile we started from.
                long scaled = (long) mVideoBitRate
                        * size.getWidth() * size.getHeight()
                        / ((long) profile.videoFrameWidth * profile.videoFrameHeight);
                mVideoWidth = size.getWidth();
                mVideoHeight = size.getHeight();
                mVideoBitRate = (int) Math.min(scaled, MAX_VIDEO_BITRATE);
                mMediaRecorder.setVideoSize(mVideoWidth, mVideoHeight);
            }
        }

        int bitRateOverride = FeatureManager.get().getInt("video.bitrate", 0);
        if (bitRateOverride > 0) {
            mVideoBitRate = Math.min(bitRateOverride, MAX_VIDEO_BITRATE);
        }
        mMediaRecorder.setVideoEncodingBitRate(mVideoBitRate);

        LogUtil.i(TAG, "[VIDEO] profile=" + mVideoQualityProfile
                + " size=" + mVideoWidth + "x" + mVideoHeight
                + " (profile " + profile.videoFrameWidth + "x" + profile.videoFrameHeight + ")"
                + " bitrate=" + mVideoBitRate + " fps=" + mVideoFrameRate);
    }

    private Size parseVideoSize(String value) {
        int x = value.indexOf('x');
        if (x <= 0 || x == value.length() - 1) {
            return null;
        }
        try {
            int width = Integer.parseInt(value.substring(0, x));
            int height = Integer.parseInt(value.substring(x + 1));
            return width > 0 && height > 0 ? new Size(width, height) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * What the encoder actually delivered, which is the only way to tell a request
     * that was honoured from one the hardware quietly throttled.
     */
    private void logRecordingMetrics(File videoFile) {
        long durationMs = mRecordingStartedAt > 0
                ? SystemClock.elapsedRealtime() - mRecordingStartedAt : 0;
        long bytes = videoFile.exists() ? videoFile.length() : 0;
        long achievedBitRate = durationMs > 0 ? bytes * 8000 / durationMs : 0;

        LogUtil.i(TAG, "[VIDEO] recorded " + mVideoWidth + "x" + mVideoHeight
                + " duration=" + durationMs + "ms"
                + " size=" + bytes + "B"
                + " bitrate_actual=" + achievedBitRate
                + " bitrate_requested=" + mVideoBitRate);

        HashMap<String, String> properties = new HashMap<>();
        properties.put("resolution", mVideoWidth + "x" + mVideoHeight);
        properties.put("duration_ms", Long.toString(durationMs));
        properties.put("file_bytes", Long.toString(bytes));
        properties.put("bitrate_requested", Integer.toString(mVideoBitRate));
        properties.put("bitrate_actual", Long.toString(achievedBitRate));
        properties.put("fps_requested", Integer.toString(mVideoFrameRate));
        mCameraMetrics.add("event_video_recorded", properties);

        mRecordingStartedAt = 0;
    }

    private void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            resetMediaRecorder();
            mMediaRecorder.release();
            mRecordingSurface.release();
            mRecordingSurface = null;
            mMediaRecorder = null;
        }
    }

    private void resetMediaRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.setOnErrorListener(null);
            mMediaRecorder.setOnInfoListener(null);
            mMediaRecorder.reset();
        }
    }

    private void restartCamera() {
        LogUtil.d(TAG, "restartCamera");
        if (isCameraInOpenState()) {
            closeCamera();
            openCamera();
            FocusManager.get().resetFocusStateToIdle();
            if (mUpdatePreviewListener != null) {
                mUpdatePreviewListener.updateVideoPreview();
            }
        }
    }

    private void setupMediaRecorderAndSurface() throws IOException {
        LogUtil.d(TAG, "Create media recorder and persistent input surface");
        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
        }
        if (mRecordingSurface == null) {
            mRecordingSurface = MediaCodec.createPersistentInputSurface();
        }
        prepareMediaRecorder();
    }

    public interface OnStatusListener {
        void onError(ErrorType errorType);
        void onMediaSaveComplete(String path);
        void onRecordStatusChange(State state);
        void onStopRecording();
    }

    public enum State {
        NOT_INITIALIZED,
        PREVIEW,
        CONTINUE_RECORDING,
        RECORDING {
            @Override
            public boolean isRecording() {
                return true;
            }
        };

        public boolean isRecording() {
            return false;
        }
    }

    public enum ErrorType {
        FILE_SAVE_FAILED,
        MAX_FILE_SIZE_REACHED,
        VIDEO_RECORD_START_FAILED,
        VIDEO_RECORD_STOP_FAILED
    }
}
