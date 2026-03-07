package com.example.apriltaglocation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 用于在相机预览上绘制AprilTag检测箭头的Overlay视图
 */
public class AprilTagOverlay extends View {
    
    private static final String TAG = "AprilTagOverlay";
    private Paint arrowPaint;     // 用于绘制箭头
    private Paint textPaint;      // 用于绘制文本
    private List<AprilTagDetector.DetectionResult> detections = new ArrayList<>(); // 使用AprilTag检测器的结果
    private int previewWidth = 0;
    private int previewHeight = 0;
    private int screenWidth = 0;
    private int screenHeight = 0;
    private boolean isFrontCamera = false;
    
    public AprilTagOverlay(Context context) {
        super(context);
        init();
    }
    
    public AprilTagOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public AprilTagOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        // 初始化箭头绘制画笔
        arrowPaint = new Paint();
        arrowPaint.setAntiAlias(true);
        arrowPaint.setColor(Color.RED);
        arrowPaint.setStyle(Paint.Style.FILL);
        arrowPaint.setStrokeWidth(8f);
        arrowPaint.setAlpha(220);
        
        // 初始化文本绘制画笔
        textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setColor(Color.YELLOW);
        textPaint.setTextSize(48);
        textPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        textPaint.setAlpha(255);
    }
    
    /**
     * 设置预览尺寸参数，用于坐标转换
     */
    public void setPreviewParams(int previewWidth, int previewHeight, int screenWidth, int screenHeight, boolean isFrontCamera) {
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.isFrontCamera = isFrontCamera;
        Log.d(TAG, "Preview params set: " + previewWidth + "x" + previewHeight + 
              ", Screen: " + screenWidth + "x" + screenHeight + 
              ", Front camera: " + isFrontCamera);
    }
    
    /**
     * 更新检测结果并刷新视图
     */
    public void updateDetections(List<AprilTagDetector.DetectionResult> detections) {
        this.detections = detections != null ? new ArrayList<>(detections) : new ArrayList<>();
        Log.d(TAG, "Updated detections: " + this.detections.size());
        // 在UI线程中更新视图
        post(() -> invalidate());
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (canvas == null || detections.isEmpty()) {
            return;
        }
        
        Log.d(TAG, "Drawing " + detections.size() + " detections");
        
        // 绘制每个检测到的标签
        for (AprilTagDetector.DetectionResult detection : detections) {
            if (detection == null) {
                continue;
            }
            
            // 检查预览尺寸是否有效
            if (previewWidth <= 0 || previewHeight <= 0) {
                Log.w(TAG, "Invalid preview dimensions: " + previewWidth + "x" + previewHeight);
                continue;
            }
            
            // 将检测坐标从相机预览空间转换到屏幕空间
            float x = (float)(detection.x * previewWidth);
            float y = (float)(detection.y * previewHeight);
            
            // 验证坐标是否在合理范围内
            if (Float.isNaN(x) || Float.isNaN(y) || Float.isInfinite(x) || Float.isInfinite(y)) {
                Log.w(TAG, "Invalid coordinates: " + x + ", " + y);
                continue;
            }
            
            // 根据置信度调整颜色和透明度
            float confidence = 0.8f; // 使用固定置信度，因为DetectionResult没有这个字段
            
            float alpha = 100 + (int)(155 * confidence); // 置信度越高越明显
            int redValue = (int)(200 + 55 * confidence); // 根据置信度调整红色强度
            redValue = Math.min(255, redValue);
            
            arrowPaint.setARGB(redValue, 100, 255, (int)alpha);
            
            // 绘制带方向的箭头
            drawDirectionalArrow(canvas, x, y, (float)detection.angle, detection.tagId, confidence);
        }
    }
    
    /**
     * 绘制带方向的箭头
     */
    private void drawDirectionalArrow(Canvas canvas, float centerX, float centerY, float angle, int id, float confidence) {
        if (canvas == null) {
            return;
        }
        
        // 箭头大小根据置信度调整
        float arrowSize = 60 + 60 * confidence;
        
        // 将角度转换为弧度
        double angleRad = Math.toRadians(angle);
        
        // 计算箭头的终点坐标（箭头指向的方向）
        float endX = (float)(centerX + Math.cos(angleRad) * arrowSize);
        float endY = (float)(centerY + Math.sin(angleRad) * arrowSize);
        
        // 计算箭头尾部坐标（反向一点）
        float startX = (float)(centerX - Math.cos(angleRad) * arrowSize * 0.3f);
        float startY = (float)(centerY - Math.sin(angleRad) * arrowSize * 0.3f);
        
        // 绘制箭身（线段）
        canvas.drawLine(startX, startY, endX, endY, arrowPaint);
        
        // 计算箭头头部的两个点
        double headAngle = Math.PI / 6; // 箭头头部张开的角度
        float headLength = arrowSize * 0.3f; // 箭头头部长度
        
        // 左侧箭头尖端
        double leftAngle = angleRad + headAngle;
        float leftX = endX - (float)(headLength * Math.cos(leftAngle));
        float leftY = endY - (float)(headLength * Math.sin(leftAngle));
        
        // 右侧箭头尖端
        double rightAngle = angleRad - headAngle;
        float rightX = endX - (float)(headLength * Math.cos(rightAngle));
        float rightY = endY - (float)(headLength * Math.sin(rightAngle));
        
        // 绘制箭头头部
        canvas.drawLine(endX, endY, leftX, leftY, arrowPaint);
        canvas.drawLine(endX, endY, rightX, rightY, arrowPaint);
        
        // 绘制标签ID
        String idText = "ID: " + id;
        canvas.drawText(idText, endX + 10, endY, textPaint);
        
        // 绘制置信度信息
        String confidenceText = "Conf: " + String.format("%.2f", confidence);
        canvas.drawText(confidenceText, endX + 10, endY + 50, textPaint);
    }
    
    /**
     * 将相机预览坐标转换为屏幕坐标
     */
    private float[] transformCoordinates(float previewX, float previewY) {
        float x = previewX;
        float y = previewY;
        
        if (previewWidth > 0 && previewHeight > 0 && screenWidth > 0 && screenHeight > 0) {
            // 按比例缩放坐标
            float scaleX = (float) screenWidth / previewWidth;
            float scaleY = (float) screenHeight / previewHeight;
            
            x = previewX * scaleX;
            y = previewY * scaleY;
            
            // 如果是前置摄像头，需要水平翻转
            if (isFrontCamera) {
                x = screenWidth - x;
            }
        }
        
        return new float[]{x, y};
    }
    
    /**
     * 清除所有检测结果
     */
    public void clearDetections() {
        this.detections.clear();
        post(() -> invalidate());
    }
}