package com.example.apriltaglocation;

import static android.Manifest.permission.CAMERA;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Size;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.apriltaglocation.databinding.ActivityMainBinding;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {CAMERA};

    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private AprilTagAnalyzer aprilTagAnalyzer;
    private Button settingsButton;
    private NetworkSender networkSender;
    private TextView coordinatesTextView; // 用于显示坐标、角度和帧率的文本视图
    
    // AprilTag IDs for corners, front and rear
    public static int[] cornerTagIds = {0, 1, 2, 3}; // Default IDs for 4 corners
    public static int frontTagId = 4; // Front tag ID
    public static int rearTagId = 5; // Rear tag ID
    public static int serverPort = 8080; // Default port for sending data

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        previewView = binding.previewView;
        settingsButton = binding.configButton;
        
        // 初始化显示坐标和角度的TextView
        coordinatesTextView = new TextView(this);
        coordinatesTextView.setTextSize(18);
        coordinatesTextView.setText("等待检测...");
        coordinatesTextView.setBackgroundColor(0x80000000); // 半透明黑色背景
        coordinatesTextView.setTextColor(0xFFFFFFFF); // 白色文字
        
        // 将TextView添加到previewView的父布局中，需要先确认previewView有父布局
        if (previewView.getParent() instanceof ViewGroup) {
            ViewGroup parentLayout = (ViewGroup) previewView.getParent();
            
            // 将TextView添加到父布局中
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = 50;
            params.leftMargin = 50;
            parentLayout.addView(coordinatesTextView, params);
        } else {
            // 如果previewView没有父布局，则使用binding.getRoot()作为父布局
            if (binding.getRoot() instanceof ViewGroup) {
                ViewGroup rootLayout = (ViewGroup) binding.getRoot();
                
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                params.topMargin = 50;
                params.leftMargin = 50;
                rootLayout.addView(coordinatesTextView, params);
            }
        }

        // 初始化网络发送器
        networkSender = new NetworkSender();

        // 检查权限并启动摄像头
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void startCamera() {
        try {
            // Load settings from SharedPreferences
            loadSettings();

            // Create AprilTagDetector with corner, front and rear tag IDs
            AprilTagDetector detector = new AprilTagDetector(cornerTagIds, frontTagId, rearTagId);

            // Initialize analyzer
            aprilTagAnalyzer = new AprilTagAnalyzer(detector, networkSender, this);
            
            // 设置检测结果回调，更新坐标显示
            aprilTagAnalyzer.setOnDetectionResultListener(result -> {
                runOnUiThread(() -> {
                    if (result != null) {
                        // 获取当前帧率并显示
                        double fps = aprilTagAnalyzer.getCurrentFps();
                        String coordsText = String.format("X: %.5f, Y: %.5f, Angle: %.5f°\nFPS: %.1f", 
                            result.x, result.y, result.angle, fps);
                        coordinatesTextView.setText(coordsText);
                    } else {
                        // 即使没有检测到结果，也要显示帧率
                        double fps = aprilTagAnalyzer.getCurrentFps();
                        coordinatesTextView.setText(String.format("未检测到标签\nFPS: %.1f", fps));
                    }
                });
            });

            ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

            cameraProviderFuture.addListener(() -> {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                    Preview preview = new Preview.Builder().build();
                    preview.setSurfaceProvider(previewView.getSurfaceProvider());

                    // 优化相机配置以提高性能
                    ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                            .setTargetResolution(new Size(640, 480))  // 降低分辨率以提高处理速度
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build();
                    imageAnalysis.setAnalyzer(cameraExecutor, aprilTagAnalyzer);

                    CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                    cameraProvider.unbindAll();
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "Error starting camera", e);
                    // If camera startup fails, show error message
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Failed to start camera: " + e.getMessage(), 
                                Toast.LENGTH_LONG).show();
                    });
                }
            }, ContextCompat.getMainExecutor(this));
        } catch (Exception e) {
            Log.e(TAG, "Error in startCamera", e);
            Toast.makeText(this, "Error starting camera: " + e.getMessage(), 
                    Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Load latest settings from SharedPreferences
     */
    private void loadSettings() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            
            // Load corner tag IDs
            int[] newCornerTagIds = {
                prefs.getInt("base_tag_1", 0),
                prefs.getInt("base_tag_2", 1),
                prefs.getInt("base_tag_3", 2),
                prefs.getInt("base_tag_4", 3)
            };
            
            // Load front and rear tag IDs
            int newFrontTagId = prefs.getInt("front_tag", 4);
            int newRearTagId = prefs.getInt("rear_tag", 5);
            
            // Load server port
            int newServerPort = prefs.getInt("server_port", 8080);
            
            // Load tag family
            String tagFamily = prefs.getString("tag_family", "tag16h5");
            
            // Update static variables
            cornerTagIds = newCornerTagIds;
            frontTagId = newFrontTagId;
            rearTagId = newRearTagId;
            serverPort = newServerPort;
            
            // Update analyzer with new settings if they've changed
            if (aprilTagAnalyzer != null) {
                aprilTagAnalyzer.updateSettings(cornerTagIds, frontTagId, rearTagId, tagFamily);
            }
            
            // Update network sender with new port
            if (networkSender != null) {
                networkSender.updateServerPort(newServerPort);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading settings", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // When Activity resumes, load the latest settings
        loadSettings();
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (!granted) {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                startCamera();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (networkSender != null) {
            networkSender.cleanup();
        }
    }
}