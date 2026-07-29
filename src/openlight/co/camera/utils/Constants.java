package openlight.co.camera.utils;

public interface Constants {
    int FOUR_BY_THREE_WIDTH_DIFF = 0xf0;
    int IMAGE_FORMAT_JPEG = 0x100;
    int IMAGE_FORMAT_LIGHT_RAW = 0x23;
    int KEEP_OUT_FOCUS_HEIGHT = 0x21c;
    int KEEP_OUT_FOCUS_WIDTH = 0x2d0;
    int KEEP_OUT_TOP_FOR_NAV_BAR = 0x82;
    long MILLI_SECOND = 1000L;
    int PI_REQUEST_CODE_RESTART_ACTIVITY = 0x87a74;
    int PI_REQUEST_LOCATION_ALARM = 0x87a75;
    int PI_REQUEST_LOCATION_UPDATE = 0x87a76;
    long SECOND = 1000000000L;

    /** Shutter values in nanoseconds, strictly descending — Util.shutterSpeedIndex
     *  binary-searches this and ShutterSpeedValues indexes into it, so the order and
     *  the length must stay in step with that enum and with the shutter_value array. */
    long[] exposureTimes = {
            0x37E11D600L, 0x2CB417800L, 0x2540BE400L, 0x1DCD65000L,
            0x165A0BC00L, 0x12A05F200L, 0xEE6B2800L, 0xBEBC2000L,
            0x9502F900L, 0x77359400L, 0x62F19700L, 0x4A817C80L,
            0x3B9ACA00L, 0x2FAF0800L, 0x23C34600L, 0x1DCD6500L,
            0x17D78400L, 0x11E1A300L, 0xEE6B280L, 0xBEBC200L,
            0x9EF21AAL, 0x7735940L, 0x5F5E100L, 0x4F790D5L,
            0x3F940AAL, 0x2FAF080L, 0x27BC86AL, 0x1FCA055L,
            0x17D7840L, 0x1312D00L, 0xFE502AL, 0xBEBC20L,
            0x989680L, 0x7F2815L, 0x65B9AAL, 0x5F5E10L,
            0x4C4B40L, 0x3F940AL, 0x2FAF08L, 0x2625A0L,
            0x1E8480L, 0x17D784L, 0x1312D0L, 0xF4240L,
            0xC3500L, 0x98968L, 0x7A120L, 0x61A80L,
            0x4C4B4L, 0x3D090L, 0x30D40L, 0x2625AL,
            0x1E848L
    };

    enum SensitivityValues {
        ISO_3200(0, 3200),
        ISO_2400(1, 2400),
        ISO_1600(2, 1600),
        ISO_1250(3, 1250),
        ISO_1000(4, 1000),
        ISO_800(5, 800),
        ISO_640(6, 640),
        ISO_500(7, 500),
        ISO_400(8, 400),
        ISO_320(9, 320),
        ISO_250(10, 250),
        ISO_200(11, 200),
        ISO_160(12, 160),
        ISO_125(13, 125),
        ISO_100(14, 100);

        private static final SensitivityValues[] sVals = values();

        private final int mIndex;
        private final int mSensitivityVal;

        SensitivityValues(int index, int sensitivityVal) {
            mIndex = index;
            mSensitivityVal = sensitivityVal;
        }

        public static SensitivityValues forIndex(int index) { return sVals[index]; }
        public static int maxIndex() { return sVals.length; }
        public int getSensitivityIndex() { return mIndex; }
        public int getSensitivityVal() { return mSensitivityVal; }
    }

    enum ShutterSpeedValues {
        SHUTTER_SPEED_15(0),
        SHUTTER_SPEED_12(1),
        SHUTTER_SPEED_10(2),
        SHUTTER_SPEED_8(3),
        SHUTTER_SPEED_6(4),
        SHUTTER_SPEED_5(5),
        SHUTTER_SPEED_4(6),
        SHUTTER_SPEED_3_2(7),
        SHUTTER_SPEED_2_5(8),
        SHUTTER_SPEED_2(9),
        SHUTTER_SPEED_166_100(10),
        SHUTTER_SPEED_125_100(11),
        SHUTTER_SPEED_1(12),
        SHUTTER_SPEED_0_8(13),
        SHUTTER_SPEED_0_6(14),
        SHUTTER_SPEED_1_2(15),
        SHUTTER_SPEED_0_3(16),
        SHUTTER_SPEED_0_4(17),
        SHUTTER_SPEED_1_4(18),
        SHUTTER_SPEED_1_5(19),
        SHUTTER_SPEED_1_6(20),
        SHUTTER_SPEED_1_8(21),
        SHUTTER_SPEED_1_10(22),
        SHUTTER_SPEED_1_12(23),
        SHUTTER_SPEED_1_15(24),
        SHUTTER_SPEED_1_20(25),
        SHUTTER_SPEED_1_24(26),
        SHUTTER_SPEED_1_30(27),
        SHUTTER_SPEED_1_40(28),
        SHUTTER_SPEED_1_50(29),
        SHUTTER_SPEED_1_60(30),
        SHUTTER_SPEED_1_80(31),
        SHUTTER_SPEED_1_100(32),
        SHUTTER_SPEED_1_120(33),
        SHUTTER_SPEED_1_150(34),
        SHUTTER_SPEED_1_160(35),
        SHUTTER_SPEED_1_200(36),
        SHUTTER_SPEED_1_240(37),
        SHUTTER_SPEED_1_320(38),
        SHUTTER_SPEED_1_400(39),
        SHUTTER_SPEED_1_500(40),
        SHUTTER_SPEED_1_640(41),
        SHUTTER_SPEED_1_800(42),
        SHUTTER_SPEED_1_1000(43),
        SHUTTER_SPEED_1_1250(44),
        SHUTTER_SPEED_1_1600(45),
        SHUTTER_SPEED_1_2000(46),
        SHUTTER_SPEED_1_3200(47),
        SHUTTER_SPEED_1_2500(48),
        SHUTTER_SPEED_1_4000(49),
        SHUTTER_SPEED_1_5000(50),
        SHUTTER_SPEED_1_6400(51),
        SHUTTER_SPEED_1_8000(52);

        private static final ShutterSpeedValues[] sVals = values();

        private final int mIndex;

        ShutterSpeedValues(int index) { mIndex = index; }

        public static ShutterSpeedValues forIndex(int index) { return sVals[index]; }
        public static int maxIndex() { return sVals.length; }
        public static long exposureTimeForIndex(int index) { return exposureTimes[index]; }
        public int getShutterSpeedIndex() { return mIndex; }
    }

    enum ExposureCompValues {
        EXPOSURE_COMP_12(0, 12),
        EXPOSURE_COMP_10(1, 10),
        EXPOSURE_COMP_08(2, 8),
        EXPOSURE_COMP_06(3, 6),
        EXPOSURE_COMP_04(4, 4),
        EXPOSURE_COMP_02(5, 2),
        EXPOSURE_COMP_00(6, 0),
        EXPOSURE_COMP_NEG_02(7, -2),
        EXPOSURE_COMP_NEG_04(8, -4),
        EXPOSURE_COMP_NEG_06(9, -6),
        EXPOSURE_COMP_NEG_08(10, -8),
        EXPOSURE_COMP_NEG_10(11, -10),
        EXPOSURE_COMP_NEG_12(12, -12);

        private static final ExposureCompValues[] sVals = values();

        private final int mIndex;
        private final int mValue;

        ExposureCompValues(int index, int value) {
            mIndex = index;
            mValue = value;
        }

        public static ExposureCompValues forIndex(int index) { return sVals[index]; }
        public static int maxIndex() { return sVals.length; }
        public int getExposureCompensationIndex() { return mIndex; }
        public int getExposureCompensationVal() { return mValue; }
    }

    enum ZoomPrimeFocalLengths {
        ZOOM_PRIME_28(28f),
        ZOOM_PRIME_35(35f),
        ZOOM_PRIME_50(50f),
        ZOOM_PRIME_70(70f),
        ZOOM_PRIME_85(85f),
        ZOOM_PRIME_105(105f),
        ZOOM_PRIME_135(135f),
        ZOOM_PRIME_150(150f);

        private final float mFocalLength;

        ZoomPrimeFocalLengths(float focalLength) { mFocalLength = focalLength; }

        public float getFocalLength() { return mFocalLength; }
    }
}
