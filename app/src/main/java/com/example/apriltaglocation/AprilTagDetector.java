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
    
    // 根据tag族类型设置不同的Hamming距离阈值
    private int maxHammingDistance;
    private double minDecisionMargin;
    
    // 用于计算位置的四个角点
    private double[][] cornerPositions = new double[4][2];
    private boolean[] cornerDetected = new boolean[4];
    
    // 添加锁对象，用于同步访问
    private final Object detectorLock = new Object();

    public AprilTagDetector(int[] baseTagIds, int vehicleTagId) {
        this.baseTagIds = baseTagIds;
        this.vehicleTagId = vehicleTagId;
        this.tagFamily = "tag16h5"; // 默认使用tag16h5
        
        updateThresholdsByTagFamily(); // 根据tag族更新阈值
        initializeDetector();
    }

    public AprilTagDetector(int[] baseTagIds, int vehicleTagId, String tagFamily) {
        this.baseTagIds = baseTagIds;
        this.vehicleTagId = vehicleTagId;
        this.tagFamily = tagFamily != null ? tagFamily : "tag16h5";
        
        updateThresholdsByTagFamily(); // 根据tag族更新阈值
        initializeDetector();
    }

    // 根据tag族类型设置不同的检测阈值
    private void updateThresholdsByTagFamily() {
        if ("tag16h5".equals(tagFamily)) {
            // 对于tag16h5，平衡性能和准确性
            maxHammingDistance = 0; // 仍然保持严格匹配，不允许错误校正
            minDecisionMargin = 20.0; // 适度降低决策边距阈值以提高性能
        } else {
            // 对于其他tag族，使用较宽松的阈值
            maxHammingDistance = 2; // 允许一定错误校正
            minDecisionMargin = 15.0; // 较低的决策边距阈值
        }
    }

    private void initializeDetector() {
        try {
            // 初始化原生库
            ApriltagNative.native_init();
            
            // 根据tag族类型设置不同的检测参数
            if ("tag16h5".equals(tagFamily)) {
                // 对于tag16h5，平衡性能和准确性
                // 参数: tagFamily, errorBits(纠错位数), decimateFactor(降低采样因子), blurSigma(模糊sigma值), nthreads(线程数)
                ApriltagNative.apriltag_init(tagFamily, 0, 1.5, 0.0, 4); // 增加降采样因子和线程数
            } else {
                // 对于其他tag族，使用相对宽松的参数
                ApriltagNative.apriltag_init(tagFamily, 2, 2, 0.0, 4); // 增加降采样因子和线程数
            }
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

    public void updateTagFamily(String newTagFamily) {
        synchronized(detectorLock) {
            if (!tagFamily.equals(newTagFamily)) {
                this.tagFamily = newTagFamily;
                updateThresholdsByTagFamily(); // 更新阈值
                // 重新初始化检测器
                try {
                    if ("tag16h5".equals(tagFamily)) {
                        // 对tag16h5使用优化的参数
                        ApriltagNative.apriltag_init(tagFamily, 0, 1.5, 0.0, 4); // 增加降采样和线程数
                    } else {
                        // 对其他族使用优化的参数
                        ApriltagNative.apriltag_init(tagFamily, 2, 1.5, 0.0, 4); // 增加降采样和线程数
                    }
                } catch (UnsatisfiedLinkError e) {
                    Log.e(TAG, "Failed to reinitialize AprilTag native library: " + e.getMessage());
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected error during AprilTag reinitialization: " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 处理图像并检测标签
     */
    @ExperimentalGetImage
    public DetectionResult processImage(ImageProxy image) {
        synchronized(detectorLock) {
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
            ApriltagDetection vehicleDetection = null;  // 只需要一个车辆标签，不再区分前后
            List<ApriltagDetection> baseDetections = new ArrayList<>();

            for (ApriltagDetection detection : detections) {
                // 验证检测质量：检查hamming距离
                if (detection.hamming > maxHammingDistance) {
                    // Hamming距离过大，跳过这个检测结果
                    continue;
                }
                
                boolean isBaseTag = false;
                for (int baseTagId : baseTagIds) {
                    if (detection.id == baseTagId) {
                        baseDetections.add(detection);
                        isBaseTag = true;
                        break;
                    }
                }
                
                if (!isBaseTag) {
                    if (detection.id == vehicleTagId) {  // 检测车辆标签，不再区分前后
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
                    DetectionResult detectionResult = new DetectionResult(normalizedX, normalizedY, angle, vehicleDetection.id);
                    Log.d(TAG, "Successfully detected: " + detectionResult.toString());
                    return detectionResult;
                }
            }

            return null;
        }
    }

    /**
     * 使用透视变换计算归一化坐标
     * 将任意四边形场地变换为标准矩形坐标系
     * 将图像上的任意四边形变换为单位正方形 [0,1]x[0,1]
     */
    private double[] calculateNormalizedCoordinates(double targetX, double targetY) {
        // 获取四个基准标签的坐标，对应单位正方形的四个角点
        // 按照约定顺序：左下、右下、左上、右上 (0, 1, 2, 3)
        double[] srcX = {cornerPositions[0][0], cornerPositions[1][0], cornerPositions[2][0], cornerPositions[3][0]};
        double[] srcY = {cornerPositions[0][1], cornerPositions[1][1], cornerPositions[2][1], cornerPositions[3][1]};
        
        // 目标单位正方形的四个角点：左下(0,1), 右下(1,1), 左上(0,0), 右上(1,0)
        double[] dstX = {0.0, 1.0, 0.0, 1.0};
        double[] dstY = {1.0, 1.0, 0.0, 0.0};
        
        // 构建透视变换矩阵系数的线性方程组
        // 透视变换公式:
        // x' = (a*x + b*y + c) / (g*x + h*y + 1)
        // y' = (d*x + e*y + f) / (g*x + h*y + 1)
        //
        // 变换为线性形式:
        // x'*(g*x + h*y + 1) = a*x + b*y + c
        // y'*(g*x + h*y + 1) = d*x + e*y + f
        //
        // 整理得:
        // a*x + b*y + c - x'*x*g - x'*y*h = x'
        // d*x + e*y + f - y'*x*g - y'*y*h = y'
        
        double[][] A = new double[8][8];
        double[] B = new double[8];
        
        // 对四个角点建立方程
        for (int i = 0; i < 4; i++) {
            // x'方程
            A[i*2][0] = srcX[i];       // a系数
            A[i*2][1] = srcY[i];       // b系数
            A[i*2][2] = 1.0;           // c系数
            A[i*2][3] = 0.0;           // d系数
            A[i*2][4] = 0.0;           // e系数
            A[i*2][5] = 0.0;           // f系数
            A[i*2][6] = -dstX[i] * srcX[i]; // g系数
            A[i*2][7] = -dstX[i] * srcY[i]; // h系数
            B[i*2] = dstX[i];          // 目标值
            
            // y'方程
            A[i*2+1][0] = 0.0;         // a系数
            A[i*2+1][1] = 0.0;         // b系数
            A[i*2+1][2] = 0.0;         // c系数
            A[i*2+1][3] = srcX[i];     // d系数
            A[i*2+1][4] = srcY[i];     // e系数
            A[i*2+1][5] = 1.0;         // f系数
            A[i*2+1][6] = -dstY[i] * srcX[i]; // g系数
            A[i*2+1][7] = -dstY[i] * srcY[i]; // h系数
            B[i*2+1] = dstY[i];        // 目标值
        }
        
        // 求解线性方程组得到变换矩阵系数
        double[] coeffs = solveLinearSystem(A, B);
        
        // 使用求得的系数计算目标点的变换后坐标
        double a = coeffs[0];
        double b = coeffs[1];
        double c = coeffs[2];
        double d = coeffs[3];
        double e = coeffs[4];
        double f = coeffs[5];
        double g = coeffs[6];
        double h = coeffs[7];
        
        // 应用透视变换
        double denominator = g * targetX + h * targetY + 1.0;
        
        // 添加安全性检查，防止分母接近0
        if (Math.abs(denominator) < 1e-6) {
            Log.e(TAG, "Perspective transformation denominator too close to zero: " + denominator);
            // 返回无效坐标
            return new double[]{0.5, 0.5}; // 返回中心点作为默认值
        }
        
        double resultX = (a * targetX + b * targetY + c) / denominator;
        double resultY = (d * targetX + e * targetY + f) / denominator;
        
        // 确保结果在合理范围内
        resultX = Math.max(0.0, Math.min(1.0, resultX));
        resultY = Math.max(0.0, Math.min(1.0, resultY));
        
        return new double[]{resultX, resultY};
    }

    /**
     * 利用单应矩阵计算小车的归一化坐标和方向
     * 传入五个点的坐标：四个基准点和一个小车点
     */
    private double[] calculateNormalizedPositionAndDirection(ApriltagDetection vehicleDetection) {
        // 提取四个基准点坐标
        double[] baseX = {cornerPositions[0][0], cornerPositions[1][0], cornerPositions[2][0], cornerPositions[3][0]};
        double[] baseY = {cornerPositions[0][1], cornerPositions[1][1], cornerPositions[2][1], cornerPositions[3][1]};
        
        // 定义在世界坐标系中的四个基准点（单位正方形）
        double[] worldX = {0.0, 1.0, 0.0, 1.0};  // 左下、右下、左上、右上
        double[] worldY = {1.0, 1.0, 0.0, 0.0};
        
        // 计算从图像坐标系到世界坐标系的单应矩阵H
        double[] H = computeHomography(baseX, baseY, worldX, worldY);
        
        if (H == null) {
            Log.e(TAG, "Failed to compute homography matrix");
            return new double[]{0.5, 0.5, 0.0}; // 返回默认值
        }
        
        // 将小车在图像中的坐标转换到世界坐标系中
        double vehicleX = vehicleDetection.c[0];
        double vehicleY = vehicleDetection.c[1];
        double[] worldCoord = applyHomography(H, vehicleX, vehicleY);
        
        // 计算小车的方向
        // 使用AprilTag的角点来确定方向
        double direction = calculateDirectionUsingHomography(vehicleDetection, H);
        
        // 确保坐标在合理范围内
        worldCoord[0] = Math.max(0.0, Math.min(1.0, worldCoord[0]));
        worldCoord[1] = Math.max(0.0, Math.min(1.0, worldCoord[1]));
        
        return new double[]{worldCoord[0], worldCoord[1], direction};
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
     * 使用单应矩阵计算小车方向
     * 方向为：透视变换后，小车上tag码在正立时的上方向，与角1到角2方向所成的角
     */
    private double calculateDirectionUsingHomography(ApriltagDetection detection, double[] H) {
        // 获取AprilTag的四个角点在图像中的坐标
        double[] cornersX = new double[4];
        double[] cornersY = new double[4];
        for (int i = 0; i < 4; i++) {
            cornersX[i] = detection.p[i * 2];     // x坐标
            cornersY[i] = detection.p[i * 2 + 1]; // y坐标
        }
        
        // 将这四个角点通过单应矩阵变换到世界坐标系
        double[] worldCornersX = new double[4];
        double[] worldCornersY = new double[4];
        for (int i = 0; i < 4; i++) {
            double[] worldPt = applyHomography(H, cornersX[i], cornersY[i]);
            worldCornersX[i] = worldPt[0];
            worldCornersY[i] = worldPt[1];
        }
        
        // 假设角点顺序为：左下、右下、右上、左上 (0, 1, 2, 3)
        // 计算在世界坐标系中标签的上方向（即从中心点到顶边中点的方向）
        double centerX = (worldCornersX[0] + worldCornersX[1] + worldCornersX[2] + worldCornersX[3]) / 4.0;
        double centerY = (worldCornersY[0] + worldCornersY[1] + worldCornersY[2] + worldCornersY[3]) / 4.0;
        double topMidX = (worldCornersX[2] + worldCornersX[3]) / 2.0;
        double topMidY = (worldCornersY[2] + worldCornersY[3]) / 2.0;
        double upDirX = topMidX - centerX;
        double upDirY = topMidY - centerY;
        double upAngle = Math.toDegrees(Math.atan2(upDirY, upDirX));
        if (upAngle < 0) upAngle += 360;
        
        // 计算角1到角2的方向（角1是左下角，角2是右下角）
        double edgeDirX = worldCornersX[1] - worldCornersX[0];
        double edgeDirY = worldCornersY[1] - worldCornersY[0];
        double edgeAngle = Math.toDegrees(Math.atan2(edgeDirY, edgeDirX));
        if (edgeAngle < 0) edgeAngle += 360;
        
        // 计算归一化方向：上方向与角1到角2方向之间的夹角
        double normalizedDirection = upAngle - edgeAngle;
        if (normalizedDirection < 0) normalizedDirection += 360;
        if (normalizedDirection >= 360) normalizedDirection -= 360;
        
        return normalizedDirection;
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

    private boolean allCornersDetected() {
        for (int i = 0; i < 4; i++) {
            if (!cornerDetected[i]) {
                // 移除不必要的日志
                return false;
            }
        }
        return true;
    }

    private double[] calculateRelativePosition(double targetX, double targetY) {
        // 获取四个基准标签的坐标
        double x1 = cornerPositions[0][0]; // 左上角
        double y1 = cornerPositions[0][1];
        double x2 = cornerPositions[1][0]; // 右上角
        double y2 = cornerPositions[1][1];
        double x3 = cornerPositions[2][0]; // 右下角
        double y3 = cornerPositions[2][1];
        double x4 = cornerPositions[3][0]; // 左下角
        double y4 = cornerPositions[3][1];
        
        // 计算场地坐标系的边界
        double minX = Math.min(Math.min(x1, x2), Math.min(x3, x4));
        double maxX = Math.max(Math.max(x1, x2), Math.max(x3, x4));
        double minY = Math.min(Math.min(y1, y2), Math.min(y3, y4));
        double maxY = Math.max(Math.max(y1, y2), Math.max(y3, y4));
        
        // 将目标坐标归一化到[0, 1]区间
        double normX = (targetX - minX) / (maxX - minX);
        double normY = (targetY - minY) / (maxY - minY);
        
        // 确保归一化坐标在[0, 1]范围内
        normX = Math.max(0, Math.min(1, normX));
        normY = Math.max(0, Math.min(1, normY));
        
        return new double[]{normX, normY};
    }

    private double calculateAngleFromCorners(ApriltagDetection detection) {
        // 计算AprilTag的方向角
        // 使用第一个和第二个角点来确定方向
        double x0 = detection.p[0]; // 第一个角点x坐标
        double y0 = detection.p[1]; // 第一个角点y坐标
        double x1 = detection.p[2]; // 第二个角点x坐标
        double y1 = detection.p[3]; // 第二个角点y坐标
        
        // 计算从第一个角点到第二个角点的角度
        double angleRad = Math.atan2(y1 - y0, x0 - x1);
        double angleDeg = Math.toDegrees(angleRad);
        
        // 确保角度在0-360度范围内
        if (angleDeg < 0) {
            angleDeg += 360;
        }
        
        return angleDeg;
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
    
    public void updateSettings(int[] baseTagIds, int vehicleTagId, String tagFamily) {
        this.baseTagIds = baseTagIds;
        this.vehicleTagId = vehicleTagId;  // 现在只需要一个车辆标签ID
        
        // 如果tag族发生变化，则重新初始化检测器
        if (!this.tagFamily.equals(tagFamily)) {
            this.tagFamily = tagFamily;
            updateThresholdsByTagFamily(); // 更新阈值
            initializeDetector();
        } else {
            this.tagFamily = tagFamily;
        }
    }


    // 检测结果内部类
    public static class DetectionResult {
        public double x;      // 归一化x坐标 (0-1)
        public double y;      // 归一化y坐标 (0-1)
        public double angle;  // 方向角度 (0-360度)
        public int tagId;     // 标签ID

        public DetectionResult(double x, double y, double angle, int tagId) {
            this.x = x;
            this.y = y;
            this.angle = angle;
            this.tagId = tagId;
        }

        @Override
        public String toString() {
            return String.format("DetectionResult{x=%.5f, y=%.5f, angle=%.5f, tagId=%d}", 
                                x, y, angle, tagId);
        }
    }
}