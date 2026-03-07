package com.example.apriltaglocation;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private EditText corner1EditText, corner2EditText, corner3EditText, corner4EditText;
    private EditText frontTagEditText;
    private EditText rearTagEditText;
    private EditText portEditText;
    private EditText serverAddressEditText;  // 添加服务器地址输入框
    private Spinner tagFamilySpinner;
    private Button saveButton;
    private TextView connectionInfoTextView;  // 添加显示连接信息的文本视图

    // 定义可用的tag族
    private static final String[] TAG_FAMILIES = {
        "tag16h5", "tag25h9", "tag36h10", "tag36h11"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initViews();

        // 创建适配器并设置给spinner
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, TAG_FAMILIES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tagFamilySpinner.setAdapter(adapter);

        // 获取存储的设置值并填充到输入框中
        loadAndFillSettings();
        
        // 显示当前连接信息
        updateConnectionInfo();

        saveButton.setOnClickListener(v -> saveSettings());
    }

    private void initViews() {
        corner1EditText = findViewById(R.id.corner1TagId);
        corner2EditText = findViewById(R.id.corner2TagId);
        corner3EditText = findViewById(R.id.corner3TagId);
        corner4EditText = findViewById(R.id.corner4TagId);
        frontTagEditText = findViewById(R.id.frontTagId);
        rearTagEditText = findViewById(R.id.rearTagId);
        portEditText = findViewById(R.id.portNumber);
        serverAddressEditText = findViewById(R.id.serverAddress);  // 初始化服务器地址输入框
        tagFamilySpinner = findViewById(R.id.tagFamilySpinner);
        saveButton = findViewById(R.id.saveSettingsButton);
        connectionInfoTextView = findViewById(R.id.connection_info_textview);  // 初始化连接信息显示文本框
    }

    private void loadAndFillSettings() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        
        corner1EditText.setText(String.valueOf(prefs.getInt("base_tag_1", 0)));
        corner2EditText.setText(String.valueOf(prefs.getInt("base_tag_2", 1)));
        corner3EditText.setText(String.valueOf(prefs.getInt("base_tag_3", 2)));
        corner4EditText.setText(String.valueOf(prefs.getInt("base_tag_4", 3)));
        frontTagEditText.setText(String.valueOf(prefs.getInt("front_tag", 4)));
        rearTagEditText.setText(String.valueOf(prefs.getInt("rear_tag", 5)));
        portEditText.setText(String.valueOf(prefs.getInt("server_port", 8080)));
        serverAddressEditText.setText(prefs.getString("server_address", "192.168.31.131"));  // 从偏好设置加载服务器地址
        
        // 获取保存的tag族，如果没有则默认为tag16h5
        String savedTagFamily = prefs.getString("tag_family", "tag16h5");
        
        // 设置spinner的选中项
        for (int i = 0; i < TAG_FAMILIES.length; i++) {
            if (TAG_FAMILIES[i].equals(savedTagFamily)) {
                tagFamilySpinner.setSelection(i);
                break;
            }
        }
    }

    private void updateConnectionInfo() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String serverAddress = prefs.getString("server_address", "192.168.31.131");
        int serverPort = prefs.getInt("server_port", 8080);
        
        String info = "发送地址: " + serverAddress + ":" + serverPort;
        if (connectionInfoTextView != null) {
            connectionInfoTextView.setText(info);
        }
    }

    private void saveSettings() {
        try {
            // 验证输入
            int corner1 = Integer.parseInt(corner1EditText.getText().toString().trim());
            int corner2 = Integer.parseInt(corner2EditText.getText().toString().trim());
            int corner3 = Integer.parseInt(corner3EditText.getText().toString().trim());
            int corner4 = Integer.parseInt(corner4EditText.getText().toString().trim());
            int frontTag = Integer.parseInt(frontTagEditText.getText().toString().trim());
            int rearTag = Integer.parseInt(rearTagEditText.getText().toString().trim());
            int port = Integer.parseInt(portEditText.getText().toString().trim());
            String serverAddress = serverAddressEditText.getText().toString().trim();  // 获取服务器地址

            // 检查Tag ID是否有效（非负数）
            if (corner1 < 0) {
                showError("Corner 1 Tag ID must be a non-negative integer");
                return;
            }
            if (corner2 < 0) {
                showError("Corner 2 Tag ID must be a non-negative integer");
                return;
            }
            if (corner3 < 0) {
                showError("Corner 3 Tag ID must be a non-negative integer");
                return;
            }
            if (corner4 < 0) {
                showError("Corner 4 Tag ID must be a non-negative integer");
                return;
            }
            if (frontTag < 0) {
                showError("Front Tag ID must be a non-negative integer");
                return;
            }
            if (rearTag < 0) {
                showError("Rear Tag ID must be a non-negative integer");
                return;
            }

            // 验证端口号
            if (port <= 0 || port > 65535) {
                showError("Port number must be between 1 and 65535");
                return;
            }

            // 验证服务器地址格式（简单验证）
            if (serverAddress.isEmpty() || !isValidIpAddress(serverAddress)) {
                showError("Please enter a valid IP address");
                return;
            }

            // 检查是否有重复的Tag ID
            if (hasDuplicateTags(corner1, corner2, corner3, corner4, frontTag, rearTag)) {
                showError("Tag IDs should not be duplicated");
                return;
            }

            // 获取选中的tag族
            String selectedTagFamily = (String) tagFamilySpinner.getSelectedItem();

            // 保存设置到SharedPreferences
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = prefs.edit();
            
            editor.putInt("base_tag_1", corner1);
            editor.putInt("base_tag_2", corner2);
            editor.putInt("base_tag_3", corner3);
            editor.putInt("base_tag_4", corner4);
            editor.putInt("front_tag", frontTag);
            editor.putInt("rear_tag", rearTag);
            editor.putInt("server_port", port);
            editor.putString("server_address", serverAddress);  // 保存服务器地址
            editor.putString("tag_family", selectedTagFamily);
            
            editor.apply();

            Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show();
            updateConnectionInfo(); // 更新连接信息显示
            finish();
        } catch (NumberFormatException e) {
            showError("Please enter valid numbers in all fields");
        }
    }
    
    // 验证IP地址格式的辅助方法
    private boolean isValidIpAddress(String ipAddress) {
        return Patterns.IP_ADDRESS.matcher(ipAddress).matches();
    }
    
    private boolean hasDuplicateTags(int... tagIds) {
        for (int i = 0; i < tagIds.length; i++) {
            for (int j = i + 1; j < tagIds.length; j++) {
                if (tagIds[i] == tagIds[j]) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}