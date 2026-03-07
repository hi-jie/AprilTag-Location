package edu.umich.eecs.april.apriltag;

public class ApriltagDetection {
    public int id;
    public int hamming;
    public double[] c;  // center
    public double[] p;  // corners

    public ApriltagDetection() {
        c = new double[2];
        p = new double[8]; // 4 corners with x,y coordinates
    }
}