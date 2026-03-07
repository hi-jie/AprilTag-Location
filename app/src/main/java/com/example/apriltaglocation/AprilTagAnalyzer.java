package com.example.apriltaglocation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.ExperimentalGetImage;

import java.nio.ByteBuffer;
import java.util.List;

public class AprilTagAnalyzer implements ImageAnalysis.Analyzer {
    private static final String TAG = "AprilTagAnalyzer";

    private final AprilTagDetector aprilTagDetector;
    private final NetworkSender networkSender;
    private final Context context;
    private AprilTagOverlay overlayView;  // 添加overlay视图成员变量
    
    // 添加检测结果回调接口
    public interface OnDetectionResultListener {
        void onDetectionResult(AprilTagDetector.DetectionResult result);
    }
    
    private OnDetectionResultListener onDetectionResultListener;

    public AprilTagAnalyzer(AprilTagDetector aprilTagDetector, NetworkSender networkSender, Context context) {
        this.aprilTagDetector = aprilTagDetector;
        this.networkSender = networkSender;
        this.context = context;
    }

    // 设置检测结果回调
    public void setOnDetectionResultListener(OnDetectionResultListener listener) {
        this.onDetectionResultListener = listener;
    }

    // 添加setOverlayView方法
    public void setOverlayView(AprilTagOverlay overlayView) {
        this.overlayView = overlayView;
    }

    @Override
    @ExperimentalGetImage
    public void analyze(@NonNull ImageProxy image) {
        try {
            // 使用AprilTag检测器处理图像
            AprilTagDetector.DetectionResult result = aprilTagDetector.processImage(image);
            
            if (result != null) {
                Log.d(TAG, "Detection result: " + result.toString());
                
                // 发送位置数据到服务器
                networkSender.sendLocationData(result.x, result.y, result.angle);
                
                // 通知监听器检测结果
                if (onDetectionResultListener != null) {
                    onDetectionResultListener.onDetectionResult(result);
                }
                
                // 更新Overlay显示检测结果
                updateOverlay(result);
            } else {
                // 即使没有检测到结果，也要通知监听器
                if (onDetectionResultListener != null) {
                    onDetectionResultListener.onDetectionResult(null);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during analysis", e);
        } finally {
            image.close();
        }
    }

    // 添加方法以更新设置
    public void updateSettings(int[] baseTagIds, int frontTagId, int rearTagId, String tagFamily) {
        aprilTagDetector.updateSettings(baseTagIds, frontTagId, rearTagId, tagFamily);
    }

    private void updateOverlay(AprilTagDetector.DetectionResult result) {
        // 如果有overlay视图，则更新它
        if (overlayView != null) {
            // 将检测结果包装成列表并更新overlay
            java.util.List<AprilTagDetector.DetectionResult> detections = 
                java.util.Collections.singletonList(result);
            overlayView.updateDetections(detections);
        }
    }
}