package com.example.apriltaglocation;

import android.util.Log;
import android.graphics.ImageFormat;
import android.media.Image;

import androidx.camera.core.ImageProxy;
import androidx.camera.core.ExperimentalGetImage;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.ArrayList;

import edu.umich.eecs.april.apriltag.ApriltagDetection;
import edu.umich.eecs.april.apriltag.ApriltagNative;

/**
 * AprilTag检测器（使用AprilTag官方库）
 */
public class AprilTagDetector {
    private static final String TAG = "AprilTagDetector";
    
    private int[] baseTagIds;
    private int vehicleTagId;  // 单个标签ID代表小车，不再区分车头和车尾
    private String tagFamily;
    private int errorBits;     // 新增：纠错位数
    private double decimateFactor; // 新增：降低采样因子
    private int nthreads;      // 新增：线程数
    
    // 用于计算位置的四个角点
    private double[][] cornerPositions = new double[4][2];
    
    // 添加锁对象，用于同步访问
    private final Object detectorLock = new Object();

    // 默认参数的构造函数
    public AprilTagDetector() {
        this(new int[] {0, 1, 2, 3}, 4, "tag36h11", 2, 1.0, 4);
    }

    public AprilTagDetector(int[] baseTagIds, int vehicleTagId, String tagFamily, int errorBits, double decimateFactor, int nthreads) {
        this.baseTagIds = baseTagIds;
        this.vehicleTagId = vehicleTagId;
        this.tagFamily = tagFamily != null ? tagFamily : "tag36h11";
        this.errorBits = errorBits;
        this.decimateFactor = decimateFactor;
        this.nthreads = nthreads;
        
        initializeDetector();
    }

    private void initializeDetector() {
        try {
            // 初始化原生库
            ApriltagNative.native_init();

            // 参数: tagFamily, errorBits(纠错位数), decimateFactor(降低采样因子), blurSigma(模糊sigma值), nthreads(线程数)
            ApriltagNative.apriltag_init(tagFamily, errorBits, decimateFactor, 0.0, nthreads);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to initialize AprilTag native library: " + e.getMessage());
            // 显示错误信息给用户
            Log.e(TAG, "Native library not loaded. Check if the native library exists and is compatible with device architecture.");
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during AprilTag initialization: " + e.getMessage(), e);
        }
    }
    
    public String getTagFamily() {
        return tagFamily;
    }

    // 获取当前设置的参数值
    public int getErrorBits() { return errorBits; }
    public double getDecimateFactor() { return decimateFactor; }
    public int getNThreads() { return nthreads; }

    /**
     * 处理图像并检测标签
     */
    @ExperimentalGetImage
    public DetectionResult processImage(ImageProxy image) {
        synchronized(detectorLock) {
            // 记录处理开始时间
            long timestamp = System.currentTimeMillis();
            
            // 获取图像字节数组
            byte[] nv21Bytes = getYUVByteArray(image);
            if (nv21Bytes == null) {
                return null;
            }

            int width = image.getWidth();
            int height = image.getHeight();

            // 使用AprilTag原生库检测标签 - 添加异常处理
            List<ApriltagDetection> detections;
            try {
                // 现在传入的是纯灰度数据（只有Y层），AprilTag算法可以处理灰度图像
                detections = ApriltagNative.apriltag_detect_yuv(nv21Bytes, width, height);
            } catch (UnsatisfiedLinkError e) {
                Log.e(TAG, "AprilTag native detection failed: " + e.getMessage());
                return null;
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error during AprilTag detection: " + e.getMessage(), e);
                return null;
            }

            // 处理检测结果
            if (detections == null || detections.isEmpty()) {
                return null;
            }

            // 查找目标标签和基准标签
            ApriltagDetection vehicleDetection = null;
            List<ApriltagDetection> baseDetections = new ArrayList<>();

            for (ApriltagDetection detection : detections) {                
                boolean isBaseTag = false;
                for (int baseTagId : baseTagIds) {
                    if (detection.id == baseTagId) {
                        baseDetections.add(detection);
                        isBaseTag = true;
                        break;
                    }
                }
                
                if (!isBaseTag) {
                    if (detection.id == vehicleTagId) { 
                        vehicleDetection = detection;
                    }
                }
            }

            // 确保找到了所有必需的标签
            if (baseDetections.size() >= 4 && vehicleDetection != null) {  // 不再需要rearDetection
                // 计算四个基准标签的位置（左下、右下、左上、右上）
                for (ApriltagDetection baseDetection : baseDetections) {
                    // 根据检测到的标签ID确定位置
                    for (int i = 0; i < baseTagIds.length; i++) {
                        if (baseDetection.id == baseTagIds[i]) {
                            cornerPositions[i][0] = baseDetection.c[0];
                            cornerPositions[i][1] = baseDetection.c[1];
                            break;
                        }
                    }
                }

                // 使用新的方法计算归一化坐标和方向（基于单应矩阵的算法）
                double[] result = calculateNormalizedPositionAndDirection(vehicleDetection);
                double normalizedX = result[0];
                double normalizedY = result[1];
                double angle = result[2];

                // 确保坐标在0-1范围内
                if (normalizedX >= 0 && normalizedX <= 1 && normalizedY >= 0 && normalizedY <= 1) {
                    DetectionResult detectionResult = new DetectionResult(normalizedX, normalizedY, angle, vehicleDetection.id, timestamp);
                    Log.d(TAG, "Successfully detected: " + detectionResult.toString());
                    return detectionResult;
                }
            }

            return null;
        }
    }

    /**
     * 利用单应矩阵计算小车的归一化坐标和方向
     * 传入五个点的坐标：四个基准点和一个小车点
     */
    private double[] calculateNormalizedPositionAndDirection(ApriltagDetection vehicleDetection) {
        
        // 计算单应矩阵H

        double[] baseX = {cornerPositions[0][0], cornerPositions[1][0], cornerPositions[2][0], cornerPositions[3][0]};
        double[] baseY = {cornerPositions[0][1], cornerPositions[1][1], cornerPositions[2][1], cornerPositions[3][1]};
        
        double[] worldX = {0.0, 1.0, 0.0, 1.0};
        double[] worldY = {0.0, 0.0, 1.0, 1.0};
        double[] H = computeHomography(baseX, baseY, worldX, worldY);
        
        if (H == null) {
            Log.e(TAG, "Failed to compute homography matrix");
            return new double[]{0.5, 0.5, 0.0}; // 返回默认值
        }
        
        // 将坐标转换到世界坐标系中

        double[] worldCenter = applyHomography(H, vehicleDetection.c[0], vehicleDetection.c[1]);        
        worldCenter[0] = Math.max(0.0, Math.min(1.0, worldCenter[0]));
        worldCenter[1] = Math.max(0.0, Math.min(1.0, worldCenter[1]));

        double[][] worldCorners = new double[4][2];
        for (int i = 0; i < 4; i++) {
            worldCorners[i] = applyHomography(H, vehicleDetection.p[i * 2], vehicleDetection.p[i * 2 + 1]);
        }

        // 矫正归一化导致的形变，确保四个角点形成一个近似正方形

        double minX = Math.min(Math.min(worldCorners[0][0], worldCorners[1][0]), Math.min(worldCorners[2][0], worldCorners[3][0]));
        double maxX = Math.max(Math.max(worldCorners[0][0], worldCorners[1][0]), Math.max(worldCorners[2][0], worldCorners[3][0]));
        double minY = Math.min(Math.min(worldCorners[0][1], worldCorners[1][1]), Math.min(worldCorners[2][1], worldCorners[3][1]));
        double maxY = Math.max(Math.max(worldCorners[0][1], worldCorners[1][1]), Math.max(worldCorners[2][1], worldCorners[3][1]));
        double deltaX = maxX - minX;
        double deltaY = maxY - minY;
        
        double targetSpan = Math.max(deltaX, deltaY);
        double scaleX = targetSpan / deltaX;
        double scaleY = targetSpan / deltaY;
        
        double centerX = (minX + maxX) / 2.0;
        double centerY = (minY + maxY) / 2.0;
        for (int i = 0; i < 4; i++) {
            worldCorners[i][0] = centerX + (worldCorners[i][0] - centerX) * scaleX;
            worldCorners[i][1] = centerY + (worldCorners[i][1] - centerY) * scaleY;
        }

        // 计算角度

        double rightMidX = (worldCorners[1][0] + worldCorners[2][0]) / 2.0;  
        double rightMidY = (worldCorners[1][1] + worldCorners[2][1]) / 2.0;
        double rightDirX = rightMidX - worldCenter[0];
        double rightDirY = rightMidY - worldCenter[1];
        
        double angleRad = Math.atan2(rightDirY, rightDirX); 
        double angleDeg = Math.toDegrees(angleRad);
        
        if (angleDeg < 0) {
            angleDeg += 360;
        }
        
        return new double[]{worldCenter[0], worldCenter[1], angleDeg};
    }

    /**
     * 计算单应矩阵 H，使得 (u,v,1) = H * (x,y,1)
     * 其中 (x,y) 是世界坐标系中的点，(u,v) 是图像坐标系中的点
     */
    private double[] computeHomography(double[] srcX, double[] srcY, double[] dstX, double[] dstY) {
        // 使用DLT算法计算单应矩阵
        double[][] A = new double[8][8];
        double[] B = new double[8];
        
        for (int i = 0; i < 4; i++) {
            // x' = (h00*x + h01*y + h02) / (h20*x + h21*y + h22)
            // y' = (h10*x + h11*y + h12) / (h20*x + h21*y + h22)
            //
            // x'*(h20*x + h21*y + h22) = h00*x + h01*y + h02
            // y'*(h20*x + h21*y + h22) = h10*x + h11*y + h12
            //
            // h00*x + h01*y + h02 - x'*x*h20 - x'*y*h21 - x'*h22 = 0
            // h10*x + h11*y + h12 - y'*x*h20 - y'*y*h21 - y'*h22 = 0
            
            double x = srcX[i];
            double y = srcY[i];
            double xp = dstX[i];
            double yp = dstY[i];
            
            // x'方程: h00*x + h01*y + h02 - x'*x*h20 - x'*y*h21 - x'*h22 = 0
            A[i*2][0] = x;
            A[i*2][1] = y;
            A[i*2][2] = 1.0;
            A[i*2][3] = 0.0;
            A[i*2][4] = 0.0;
            A[i*2][5] = 0.0;
            A[i*2][6] = -xp * x;
            A[i*2][7] = -xp * y;
            B[i*2] = xp;
            
            // y'方程: h10*x + h11*y + h12 - y'*x*h20 - y'*y*h21 - y'*h22 = 0
            A[i*2+1][0] = 0.0;
            A[i*2+1][1] = 0.0;
            A[i*2+1][2] = 0.0;
            A[i*2+1][3] = x;
            A[i*2+1][4] = y;
            A[i*2+1][5] = 1.0;
            A[i*2+1][6] = -yp * x;
            A[i*2+1][7] = -yp * y;
            B[i*2+1] = yp;
        }
        
        double[] h = solveLinearSystem(A, B);
        // h[0..7] 包含了单应矩阵的前8个元素，最后一个元素设为1
        // h00, h01, h02, h10, h11, h12, h20, h21 (h22 = 1)
        // 为了标准化，我们将整个矩阵除以h22的实际值
        double[][] H_matrix = new double[3][3];
        H_matrix[0][0] = h[0];
        H_matrix[0][1] = h[1];
        H_matrix[0][2] = h[2];
        H_matrix[1][0] = h[3];
        H_matrix[1][1] = h[4];
        H_matrix[1][2] = h[5];
        H_matrix[2][0] = h[6];
        H_matrix[2][1] = h[7];
        H_matrix[2][2] = 1.0;
        
        // 将矩阵展开为一维数组返回
        double[] result = new double[9];
        int idx = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                result[idx++] = H_matrix[i][j];
            }
        }
        return result;
    }

    /**
     * 应用单应矩阵变换
     */
    private double[] applyHomography(double[] H, double x, double y) {
        // (x', y', w) = H * (x, y, 1)
        double x_prime = H[0]*x + H[1]*y + H[2];
        double y_prime = H[3]*x + H[4]*y + H[5];
        double w = H[6]*x + H[7]*y + H[8];
        
        // 齐次坐标的去齐次化
        if (Math.abs(w) < 1e-10) {
            Log.e(TAG, "Homography transformation resulted in near-zero w: " + w);
            return new double[]{x, y}; // 返回原始坐标作为默认值
        }
        return new double[]{x_prime/w, y_prime/w};
    }

    /**
     * 解线性方程组 AX = B
     * 使用高斯消元法
     */
    private double[] solveLinearSystem(double[][] A, double[] B) {
        int n = B.length;
        double[][] augmented = new double[n][n + 1];

        // 构造增广矩阵
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                augmented[i][j] = A[i][j];
            }
            augmented[i][n] = B[i];
        }

        // 高斯消元
        for (int i = 0; i < n; i++) {
            // 寻找主元
            int maxRow = i;
            for (int k = i + 1; k < n; k++) {
                if (Math.abs(augmented[k][i]) > Math.abs(augmented[maxRow][i])) {
                    maxRow = k;
                }
            }

            // 交换行
            double[] temp = augmented[maxRow];
            augmented[maxRow] = augmented[i];
            augmented[i] = temp;

            // 消元
            for (int k = i + 1; k < n; k++) {
                double factor = augmented[k][i] / augmented[i][i];
                for (int j = i; j < n + 1; j++) {
                    augmented[k][j] -= factor * augmented[i][j];
                }
            }
        }

        // 回代求解
        double[] solution = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            solution[i] = augmented[i][n];
            for (int j = i + 1; j < n; j++) {
                solution[i] -= augmented[i][j] * solution[j];
            }
            solution[i] /= augmented[i][i];
        }

        return solution;
    }


    private byte[] getYUVByteArray(ImageProxy image) {
        Image mediaImage = image.getImage();
        if (mediaImage == null) {
            return null;
        }

        // 检查格式
        int format = mediaImage.getFormat();
        if (format != ImageFormat.YUV_420_888 && format != ImageFormat.NV21) {
            mediaImage.close();
            return null;
        }

        // 处理YUV_420_888格式 - 只取Y层数据
        if (format == ImageFormat.YUV_420_888) {
            return convertYuv420ToGrayscale(mediaImage);
        } else {
            // 处理NV21格式 - 也需要只取Y层
            return convertNv21ToGrayscale(mediaImage);
        }
    }
    
    /**
     * 从YUV_420_888格式图像中只提取Y层（灰度）数据
     */
    private byte[] convertYuv420ToGrayscale(Image mediaImage) {
        Image.Plane[] planes = mediaImage.getPlanes();
        int width = mediaImage.getWidth();
        int height = mediaImage.getHeight();
        
        // 确保有足够的平面
        if (planes.length < 3) {
            mediaImage.close();
            return null;
        }

        ByteBuffer yBuffer = planes[0].getBuffer(); // Y平面

        if (yBuffer == null) {
            mediaImage.close();
            return null;
        }

        // 只复制Y平面的数据（灰度图）
        int ySize = yBuffer.remaining();
        byte[] grayscale = new byte[ySize];
        yBuffer.get(grayscale, 0, ySize);

        mediaImage.close();
        return grayscale;
    }

    /**
     * 从NV21格式图像中只提取Y层（灰度）数据
     */
    private byte[] convertNv21ToGrayscale(Image mediaImage) {
        Image.Plane[] planes = mediaImage.getPlanes();
        if (planes.length < 1) {
            mediaImage.close();
            return null;
        }

        ByteBuffer buffer = planes[0].getBuffer(); // Y平面
        int ySize = buffer.remaining();
        byte[] grayscale = new byte[ySize];
        buffer.get(grayscale, 0, ySize);

        mediaImage.close();
        return grayscale;
    }
    
    // 统一的更新设置方法，包含所有参数
    public void updateSettings(int[] baseTagIds, int vehicleTagId, String tagFamily, int errorBits, double decimateFactor, int nthreads) {
        boolean needReinit = false;
        // 检查是否需要重新初始化检测器
        if (!this.tagFamily.equals(tagFamily) || this.errorBits != errorBits || this.nthreads != nthreads) {
            needReinit = true;
        }

        this.baseTagIds = baseTagIds;
        this.vehicleTagId = vehicleTagId;
        this.tagFamily = tagFamily;
        this.errorBits = errorBits;
        this.decimateFactor = decimateFactor;
        this.nthreads = nthreads;

        if (needReinit) {
            initializeDetector();
        }
    }


    // 检测结果内部类
    public static class DetectionResult {
        public double x;      // 归一化x坐标 (0-1)
        public double y;      // 归一化y坐标 (0-1)
        public double angle;  // 方向角度 (0-360度)
        public int tagId;     // 标签ID
        public long timestamp; // 时间戳

        public DetectionResult(double x, double y, double angle, int tagId, long timestamp) {
            this.x = x;
            this.y = y;
            this.angle = angle;
            this.tagId = tagId;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return String.format("DetectionResult{x=%.5f, y=%.5f, angle=%.5f, tagId=%d, timestamp=%d}", 
                                x, y, angle, tagId, timestamp);
        }
    }
}