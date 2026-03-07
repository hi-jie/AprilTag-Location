package com.example.apriltaglocation;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import android.preference.PreferenceManager;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class NetworkSender {
    private static final String TAG = "NetworkSender";
    // 创建一个具有超时设置的OkHttpClient实例
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)      // 增加连接超时时间
            .writeTimeout(15, TimeUnit.SECONDS)       // 增加写入超时时间
            .readTimeout(30, TimeUnit.SECONDS)        // 增加读取超时时间
            // 允许自签名证书，方便开发调试
            .hostnameVerifier((hostname, session) -> true)
            .build();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private int serverPort = 8080; // 默认端口
    private String serverAddress = "192.168.31.131"; // 默认IP

    public NetworkSender() {
        // 加载默认设置
        loadSettings();
    }

    public void sendLocationData(double x, double y, double angle) {
        Log.d(TAG, String.format("Received location data - x: %.3f, y: %.3f, angle: %.1f", x, y, angle));
        
        // 创建JSON数据
        JSONObject json = new JSONObject();
        try {
            json.put("x", x);
            json.put("y", y);
            json.put("angle", angle);
        } catch (Exception e) {
            Log.e(TAG, "Error creating JSON", e);
            return;
        }

        // 从SharedPreferences获取最新的服务器配置
        String currentServerAddress;
        int currentServerPort;

        try {
            Context context = MyApplication.getContext();
            if (context != null) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                currentServerPort = prefs.getInt("server_port", 8080);
                currentServerAddress = prefs.getString("server_address", "192.168.31.131");
            } else {
                Log.e(TAG, "Context is null, using defaults");
                currentServerPort = this.serverPort;
                currentServerAddress = this.serverAddress;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading preferences", e);
            // 使用实例变量作为备用
            currentServerPort = this.serverPort;
            currentServerAddress = this.serverAddress;
        }

        // 确保服务器地址不为空
        if (currentServerAddress == null || currentServerAddress.trim().isEmpty()) {
            Log.e(TAG, "Server address is empty, using default");
            currentServerAddress = "192.168.31.131";
        }

        String url = "http://" + currentServerAddress + ":" + currentServerPort + "/api/location";
        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .build();

        Log.d(TAG, "Sending location data to: " + url);
        Log.d(TAG, "Payload: " + json.toString());

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Network request failed", e);
                Log.e(TAG, "Attempted URL: " + url);
                // 在主线程上显示Toast，添加对context的空值检查
                mainHandler.post(() -> {
                    Context context = MyApplication.getContext();
                    if (context != null) {
                        String errorMessage = "Failed to send location: " + e.getClass().getSimpleName() + 
                                             " - " + e.getMessage();
                        Log.e(TAG, "Detailed error: " + errorMessage);
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show();
                    } else {
                        Log.e(TAG, "Context is null, cannot show toast: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseBodyStr;
                if (response.isSuccessful()) {
                    Log.d(TAG, "Location data sent successfully, code: " + response.code());
                    responseBodyStr = "Success";
                    // 在主线程上显示成功消息（可选）
                    mainHandler.post(() -> {
                        Context context = MyApplication.getContext();
                        if (context != null) {
                            Log.d(TAG, "Response successful: " + response.code());
                            // 添加成功提示
                            Toast.makeText(context, "Location data sent successfully", 
                                          Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    Log.e(TAG, "Server error: " + response.code());
                    String responseContent = "";
                    try {
                        responseContent = response.body().string();
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading response body", e);
                    }
                    Log.e(TAG, "Response body: " + responseContent);
                    responseBodyStr = responseContent;
                    
                    // 在主线程上显示错误消息
                    mainHandler.post(() -> {
                        Context context = MyApplication.getContext();
                        if (context != null) {
                            Toast.makeText(context, 
                                    "Server error " + response.code() + ": " + responseBodyStr, 
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
                response.body().close();
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
        // 清理资源
        client.dispatcher().cancelAll();
    }
}