package com.example.apriltaglocation;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private EditText corner1EditText, corner2EditText, corner3EditText, corner4EditText;
    private EditText vehicleTagEditText;  // 单个车辆标签编辑框，不再区分前后
    private Spinner tagFamilySpinner;
    private Button saveButton;

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

        saveButton.setOnClickListener(v -> saveSettings());
    }

    private void initViews() {
        corner1EditText = findViewById(R.id.corner1TagId);
        corner2EditText = findViewById(R.id.corner2TagId);
        corner3EditText = findViewById(R.id.corner3TagId);
        corner4EditText = findViewById(R.id.corner4TagId);
        vehicleTagEditText = findViewById(R.id.vehicleTagId);  // 更改了控件ID
        tagFamilySpinner = findViewById(R.id.tagFamilySpinner);
        saveButton = findViewById(R.id.saveSettingsButton);
    }

    private void loadAndFillSettings() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        
        corner1EditText.setText(String.valueOf(prefs.getInt("base_tag_1", 0)));
        corner2EditText.setText(String.valueOf(prefs.getInt("base_tag_2", 1)));
        corner3EditText.setText(String.valueOf(prefs.getInt("base_tag_3", 2)));
        corner4EditText.setText(String.valueOf(prefs.getInt("base_tag_4", 3)));
        vehicleTagEditText.setText(String.valueOf(prefs.getInt("front_tag", 4)));  // 仍使用front_tag键存储车辆标签ID
        
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

    private void saveSettings() {
        try {
            // 验证输入
            int corner1 = Integer.parseInt(corner1EditText.getText().toString().trim());
            int corner2 = Integer.parseInt(corner2EditText.getText().toString().trim());
            int corner3 = Integer.parseInt(corner3EditText.getText().toString().trim());
            int corner4 = Integer.parseInt(corner4EditText.getText().toString().trim());
            int vehicleTag = Integer.parseInt(vehicleTagEditText.getText().toString().trim());  // 单个车辆标签ID

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
            if (vehicleTag < 0) {
                showError("Vehicle Tag ID must be a non-negative integer");
                return;
            }

            // 检查是否有重复的Tag ID
            if (hasDuplicateTags(corner1, corner2, corner3, corner4, vehicleTag)) {
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
            editor.putInt("front_tag", vehicleTag);  // 保存车辆标签ID到front_tag键
            editor.putString("tag_family", selectedTagFamily);
            
            editor.apply();

            Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show();
            finish();
        } catch (NumberFormatException e) {
            showError("Please enter valid numbers in all fields");
        }
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