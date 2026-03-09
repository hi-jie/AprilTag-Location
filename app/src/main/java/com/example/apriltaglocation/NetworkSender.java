package com.example.apriltaglocation;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import android.preference.PreferenceManager;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkSender {
    private static final String TAG = "NetworkSender";
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private int serverPort = 8080; // 默认端口
    private String serverAddress = "192.168.31.131"; // 默认IP
    
    // 添加变量来跟踪上次发送的数据，实现仅在变化时发送
    private double lastX = Double.NaN;
    private double lastY = Double.NaN;
    private double lastAngle = Double.NaN;
    private long lastTimestamp = -1; // 新增时间戳比较变量
    
    // 添加发送频率控制
    private long lastSendTime = 0;
    private static final long MIN_SEND_INTERVAL = 10; // 最小发送间隔10ms (约100Hz)
    
    // UDP socket相关
    private DatagramSocket udpSocket = null;
    private InetAddress serverInetAddress = null;
    
    // 线程池用于UDP发送
    private final ExecutorService udpExecutor = Executors.newSingleThreadExecutor();

    public NetworkSender() {
        // 加载默认设置
        loadSettings();
        initUdpSocket();
    }
    
    private void initUdpSocket() {
        try {
            udpSocket = new DatagramSocket();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create UDP socket", e);
        }
    }

    public void sendLocationData(double x, double y, double angle) {
        // 旧方法，保留向后兼容性
        sendLocationData(x, y, angle, System.currentTimeMillis());
    }

    public void sendLocationData(double x, double y, double angle, long timestamp) {
        // 检查数据是否变化，如果没有变化则不发送
        if (Double.isNaN(lastX) || lastX != x || lastY != y || lastAngle != angle || lastTimestamp != timestamp) {
            // 检查是否达到了最小发送间隔
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastSendTime >= MIN_SEND_INTERVAL) {
                performSend(x, y, angle, timestamp);
            }
        }
    }

    private void performSend(double x, double y, double angle, long timestamp) {
        // 更新上次发送的数据
        lastX = x;
        lastY = y;
        lastAngle = angle;
        lastTimestamp = timestamp;
        lastSendTime = System.currentTimeMillis();
        
        // 从SharedPreferences获取最新的服务器配置
        int tempServerPort;
        String tempServerAddress;

        try {
            Context context = MyApplication.getContext();
            if (context != null) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                tempServerPort = prefs.getInt("server_port", 8080);
                tempServerAddress = prefs.getString("server_address", "192.168.31.131");
            } else {
                Log.e(TAG, "Context is null, using defaults");
                tempServerPort = this.serverPort;
                tempServerAddress = this.serverAddress;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading preferences", e);
            // 使用实例变量作为备用
            tempServerPort = this.serverPort;
            tempServerAddress = this.serverAddress;
        }

        // 确保服务器地址不为空
        if (tempServerAddress == null || tempServerAddress.trim().isEmpty()) {
            Log.e(TAG, "Server address is empty, using default");
            tempServerAddress = "192.168.31.131";
        }

        // 创建final变量以供lambda表达式使用
        final int finalServerPort = tempServerPort;
        final String finalServerAddress = tempServerAddress;

        // 创建带时间戳的数据格式：x,y,angle,timestamp
        String payload = String.format("%.5f,%.5f,%.5f,%d", x, y, angle, timestamp);
        
        // 在单独的线程中发送UDP数据
        udpExecutor.execute(() -> {
            try {
                InetAddress address = InetAddress.getByName(finalServerAddress);
                byte[] buffer = payload.getBytes();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, 
                    address, finalServerPort);
                    
                if (udpSocket != null && !udpSocket.isClosed()) {
                    udpSocket.send(packet);
                    Log.d(TAG, "UDP packet sent: " + payload + " to " + finalServerAddress + ":" + finalServerPort);
                } else {
                    Log.e(TAG, "UDP socket is null or closed");
                }
            } catch (Exception e) {
                Log.e(TAG, "UDP send failed: " + e.getMessage(), e);
            }
        });
    }

    public void updateServerPort(int newPort) {
        this.serverPort = newPort;
    }

    private void loadSettings() {
        // 从偏好设置加载服务器配置
        try {
            Context context = MyApplication.getContext();
            if (context != null) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                this.serverPort = prefs.getInt("server_port", 8080);
                this.serverAddress = prefs.getString("server_address", "192.168.31.131");
            } else {
                Log.e(TAG, "Context is null, cannot load settings");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading settings", e);
        }
    }

    public void cleanup() {
        // 关闭UDP socket和线程池
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
        udpExecutor.shutdown();
    }
}