package com.example.apriltaglocation;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.Log;
import java.util.ArrayList;
import edu.umich.eecs.april.apriltag.ApriltagDetection;

/**
 * AprilTag实用工具类，提供通用的检测和渲染功能
 */
public class AprilTagUtils {
    private static final String TAG = "AprilTagUtils";

    /**
     * 渲染检测到的AprilTag
     *
     * @param detection 检测结果
     * @param canvas 画布
     * @param cameraWidth 相机宽度
     * @param cameraHeight 相机高度
     */
    public static void renderDetection(ApriltagDetection detection, Canvas canvas, int cameraWidth, int cameraHeight) {
        Paint fillPaint = new Paint();
        fillPaint.setColor(Color.GREEN);
        fillPaint.setAlpha(128);
        fillPaint.setStyle(Paint.Style.FILL);

        Paint borderPaint = new Paint();
        final int[] borderColors = new int[]{Color.GREEN, Color.WHITE, Color.WHITE, Color.RED};
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(10);

        // 计算缩放比例，将检测坐标转换为画布坐标
        float scaleX = (float) canvas.getWidth() / cameraWidth;
        float scaleY = (float) canvas.getHeight() / cameraHeight;

        double[] points = detection.p;
        if (points == null || points.length != 8) {
            Log.w(TAG, "Invalid detection coordinates");
            return;
        }

        // 转换检测点到画布坐标
        float[] xPointsCanvas = new float[4];
        float[] yPointsCanvas = new float[4];
        for (int i = 0; i < 4; i++) {
            xPointsCanvas[i] = (float) (points[i * 2] * scaleX); // x坐标
            yPointsCanvas[i] = (float) (points[i * 2 + 1] * scaleY); // y坐标
        }

        // 渲染检测框的填充区域
        Path fillPath = new Path();
        for (int i = 0; i < 4; i++) {
            if (i == 0) {
                fillPath.moveTo(xPointsCanvas[i], yPointsCanvas[i]);
            } else {
                fillPath.lineTo(xPointsCanvas[i], yPointsCanvas[i]);
            }
        }
        fillPath.close();
        canvas.drawPath(fillPath, fillPaint);

        // 渲染检测框的边框
        int colorIndex = 0;
        for (int i = 0; i < 4; i++) {
            Path borderPath = new Path();
            borderPaint.setColor(borderColors[colorIndex++ % borderColors.length]);

            borderPath.moveTo(xPointsCanvas[i], yPointsCanvas[i]);
            borderPath.lineTo(xPointsCanvas[(i + 1) % 4], yPointsCanvas[(i + 1) % 4]);
            canvas.drawPath(borderPath, borderPaint);
        }

        // 在检测框中心绘制标签ID
        Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(100);
        String tagId = String.valueOf(detection.id);
        float textWidth = textPaint.measureText(tagId);
        float textHeight = textPaint.getFontMetrics().descent - textPaint.getFontMetrics().ascent;
        float textX = (float) (detection.c[0] * scaleX - textWidth / 2);
        float textY = (float) (detection.c[1] * scaleY + textHeight / 2 - textPaint.getFontMetrics().descent);
        canvas.drawText(tagId, textX, textY, textPaint);
    }

    /**
     * 批量渲染检测结果
     *
     * @param detections 检测结果列表
     * @param canvas 画布
     * @param cameraWidth 相机宽度
     * @param cameraHeight 相机高度
     */
    public static void renderDetections(ArrayList<ApriltagDetection> detections, Canvas canvas, 
                                        int cameraWidth, int cameraHeight) {
        for (ApriltagDetection detection : detections) {
            renderDetection(detection, canvas, cameraWidth, cameraHeight);
        }
    }

    /**
     * 根据标签族名称获取描述
     *
     * @param tagFamily 标签族名称
     * @return 标签族描述
     */
    public static String getTagFamilyDescription(String tagFamily) {
        switch (tagFamily) {
            case "tag16h5":
                return "16h5 (4x4, 5-bit)";
            case "tag25h9":
                return "25h9 (5x5, 9-bit)";
            case "tag36h10":
                return "36h10 (6x6, 10-bit)";
            case "tag36h11":
                return "36h11 (6x6, 11-bit)";
            default:
                return tagFamily;
        }
    }
}