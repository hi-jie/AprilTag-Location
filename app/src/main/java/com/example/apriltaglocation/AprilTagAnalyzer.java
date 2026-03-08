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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AprilTagAnalyzer implements ImageAnalysis.Analyzer {
    private static final String TAG = "AprilTagAnalyzer";

    private final AprilTagDetector aprilTagDetector;
    private final NetworkSender networkSender;
    private final Context context;
    
    // 用于异步网络发送的线程池
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    
    // 帧率计算相关变量
    private long lastAnalysisTime = 0;
    private double currentFps = 0.0;
    private static final int FPS_CALCULATION_WINDOW = 10; // 用于计算平均FPS的窗口大小
    private java.util.Queue<Long> frameTimes;
    
    // 添加检测结果回调接口
    public interface OnDetectionResultListener {
        void onDetectionResult(AprilTagDetector.DetectionResult result);
    }
    
    private OnDetectionResultListener onDetectionResultListener;

    public AprilTagAnalyzer(AprilTagDetector aprilTagDetector, NetworkSender networkSender, Context context) {
        this.aprilTagDetector = aprilTagDetector;
        this.networkSender = networkSender;
        this.context = context;
        this.frameTimes = new java.util.LinkedList<>();
    }

    // 设置检测结果回调
    public void setOnDetectionResultListener(OnDetectionResultListener listener) {
        this.onDetectionResultListener = listener;
    }


    @Override
    @ExperimentalGetImage
    public void analyze(@NonNull ImageProxy image) {
        long overallStartTime = System.currentTimeMillis();
        long currentTime = System.currentTimeMillis();
        
        // 计算帧率
        if (lastAnalysisTime > 0) {
            long deltaTime = currentTime - lastAnalysisTime;
            if (deltaTime > 0) {
                double currentFrameFps = 1000.0 / deltaTime;
                
                // 维护一个队列来计算平均帧率
                frameTimes.offer(currentTime);
                while (frameTimes.size() > FPS_CALCULATION_WINDOW) {
                    frameTimes.poll();
                }
                
                // 计算平均FPS
                if (frameTimes.size() >= 2) {
                    long startTime = frameTimes.peek();
                    long endTime = frameTimes.peek();
                    if (frameTimes.size() > 1) {
                        endTime = frameTimes.peek();
                        Long[] timesArray = frameTimes.toArray(new Long[frameTimes.size()]);
                        endTime = timesArray[timesArray.length - 1];
                    }
                    long totalTime = endTime - startTime;
                    currentFps = (frameTimes.size() - 1) * 1000.0 / totalTime;
                } else {
                    currentFps = currentFrameFps;
                }
            }
        }
        lastAnalysisTime = currentTime;

        try {
            // 使用AprilTag检测器处理图像 - 测量检测时间
            long detectionStartTime = System.currentTimeMillis();
            AprilTagDetector.DetectionResult result = aprilTagDetector.processImage(image);
            long detectionEndTime = System.currentTimeMillis();
            long detectionDuration = detectionEndTime - detectionStartTime;
            
            if (result != null) {
                // 仅输出最终检测结果
                Log.d(TAG, String.format("检测到标签 - X: %.5f, Y: %.5f, Angle: %.5f°", result.x, result.y, result.angle));
                
                // 在后台线程发送位置数据，避免阻塞图像分析
                networkExecutor.execute(() -> {
                    networkSender.sendLocationData(result.x, result.y, result.angle);
                });
                
                // 通知监听器检测结果
                if (onDetectionResultListener != null) {
                    onDetectionResultListener.onDetectionResult(result);
                }
                
            } else {
                // 仅在长时间未检测到标签时输出一次日志
                if (detectionDuration > 100) {
                    Log.d(TAG, "未检测到标签");
                }
                
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

    // 获取当前帧率
    public double getCurrentFps() {
        return currentFps;
    }

}