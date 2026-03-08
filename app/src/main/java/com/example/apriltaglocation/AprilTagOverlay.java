package com.example.apriltaglocation;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

/**
 * 用于占位的Overlay视图，目前不绘制任何内容
 */
public class AprilTagOverlay extends View {
    
    
    public AprilTagOverlay(Context context) {
        super(context);
    }
    
    public AprilTagOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    public AprilTagOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
    
    
    @Override
    protected void onDraw(Canvas canvas) {
        // 不绘制任何内容
    }
    
}