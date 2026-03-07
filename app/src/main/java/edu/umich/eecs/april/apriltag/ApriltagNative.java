package edu.umich.eecs.april.apriltag;

import java.util.List;

public class ApriltagNative {
    static {
        System.loadLibrary("apriltag");
    }

    public static native void native_init();

    public static native void apriltag_init(String tagFamilyStr, int errorBits,
                                            double decimateFactor, double blurSigma, int nthreads);

    public static native List<ApriltagDetection> apriltag_detect_yuv(byte[] yuvBytes, int width, int height);
}