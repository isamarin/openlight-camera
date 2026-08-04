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
            0x6FB0EC548L, 0x5D21DBA00L, 0x4A817C800L,
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
        ISO_12800(0, 12800),
        ISO_10000(1, 10000),
        ISO_8000(2, 8000),
        ISO_6400(3, 6400),
        ISO_5000(4, 5000),
        ISO_4000(5, 4000),
        ISO_3200(6, 3200),
        ISO_2400(7, 2400),
        ISO_1600(8, 1600),
        ISO_1250(9, 1250),
        ISO_1000(10, 1000),
        ISO_800(11, 800),
        ISO_640(12, 640),
        ISO_500(13, 500),
        ISO_400(14, 400),
        ISO_320(15, 320),
        ISO_250(16, 250),
        ISO_200(17, 200),
        ISO_160(18, 160),
        ISO_125(19, 125),
        ISO_100(20, 100);

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
        SHUTTER_SPEED_30(0),
        SHUTTER_SPEED_25(1),
        SHUTTER_SPEED_20(2),
        SHUTTER_SPEED_15(3),
        SHUTTER_SPEED_12(4),
        SHUTTER_SPEED_10(5),
        SHUTTER_SPEED_8(6),
        SHUTTER_SPEED_6(7),
        SHUTTER_SPEED_5(8),
        SHUTTER_SPEED_4(9),
        SHUTTER_SPEED_3_2(10),
        SHUTTER_SPEED_2_5(11),
        SHUTTER_SPEED_2(12),
        SHUTTER_SPEED_166_100(13),
        SHUTTER_SPEED_125_100(14),
        SHUTTER_SPEED_1(15),
        SHUTTER_SPEED_0_8(16),
        SHUTTER_SPEED_0_6(17),
        SHUTTER_SPEED_1_2(18),
        SHUTTER_SPEED_0_3(19),
        SHUTTER_SPEED_0_4(20),
        SHUTTER_SPEED_1_4(21),
        SHUTTER_SPEED_1_5(22),
        SHUTTER_SPEED_1_6(23),
        SHUTTER_SPEED_1_8(24),
        SHUTTER_SPEED_1_10(25),
        SHUTTER_SPEED_1_12(26),
        SHUTTER_SPEED_1_15(27),
        SHUTTER_SPEED_1_20(28),
        SHUTTER_SPEED_1_24(29),
        SHUTTER_SPEED_1_30(30),
        SHUTTER_SPEED_1_40(31),
        SHUTTER_SPEED_1_50(32),
        SHUTTER_SPEED_1_60(33),
        SHUTTER_SPEED_1_80(34),
        SHUTTER_SPEED_1_100(35),
        SHUTTER_SPEED_1_120(36),
        SHUTTER_SPEED_1_150(37),
        SHUTTER_SPEED_1_160(38),
        SHUTTER_SPEED_1_200(39),
        SHUTTER_SPEED_1_240(40),
        SHUTTER_SPEED_1_320(41),
        SHUTTER_SPEED_1_400(42),
        SHUTTER_SPEED_1_500(43),
        SHUTTER_SPEED_1_640(44),
        SHUTTER_SPEED_1_800(45),
        SHUTTER_SPEED_1_1000(46),
        SHUTTER_SPEED_1_1250(47),
        SHUTTER_SPEED_1_1600(48),
        SHUTTER_SPEED_1_2000(49),
        SHUTTER_SPEED_1_3200(50),
        SHUTTER_SPEED_1_2500(51),
        SHUTTER_SPEED_1_4000(52),
        SHUTTER_SPEED_1_5000(53),
        SHUTTER_SPEED_1_6400(54),
        SHUTTER_SPEED_1_8000(55);

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
