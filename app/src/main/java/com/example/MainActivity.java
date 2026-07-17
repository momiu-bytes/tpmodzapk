package com.example;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int OVERLAY_PERMISSION_REQ_CODE = 5469;
    private static final String PREFS_NAME = "LagTpModPrefs";
    private static final String KEY_LOGGED_IN = "isLoggedIn";

    private LinearLayout loginLayout;
    private LinearLayout controlLayout;
    private EditText etKey;
    private TextView tvStatus;
    private Button btnToggleService;
    private Button btnRequestPermission;

    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Bind layouts and views
        loginLayout = findViewById(R.id.login_layout);
        controlLayout = findViewById(R.id.control_layout);
        etKey = findViewById(R.id.et_key);
        tvStatus = findViewById(R.id.tv_status);
        btnToggleService = findViewById(R.id.btn_toggle_service);
        btnRequestPermission = findViewById(R.id.btn_request_permission);

        Button btnLogin = findViewById(R.id.btn_login);

        // Check login state
        boolean isLoggedIn = sharedPreferences.getBoolean(KEY_LOGGED_IN, false);
        if (isLoggedIn) {
            showControlLayout();
        } else {
            showLoginLayout();
        }

        // Handle Login button click
        btnLogin.setOnClickListener(v -> {
            String enteredKey = etKey.getText().toString().trim();
            if ("123".equals(enteredKey)) {
                sharedPreferences.edit().putBoolean(KEY_LOGGED_IN, true).apply();
                Toast.makeText(MainActivity.this, "Đăng nhập thành công", Toast.LENGTH_SHORT).show();
                showControlLayout();
            } else {
                Toast.makeText(MainActivity.this, "Sai key", Toast.LENGTH_SHORT).show();
            }
        });

        // Handle Toggle Service button
        btnToggleService.setOnClickListener(v -> {
            if (isOverlayPermissionGranted()) {
                toggleOverlayService();
            } else {
                Toast.makeText(MainActivity.this, "Vui lòng cấp quyền trước", Toast.LENGTH_SHORT).show();
                requestOverlayPermission();
            }
        });

        // Handle Request Permission button
        btnRequestPermission.setOnClickListener(v -> requestOverlayPermission());
    }

    private void showLoginLayout() {
        loginLayout.setVisibility(View.VISIBLE);
        controlLayout.setVisibility(View.GONE);
    }

    private void showControlLayout() {
        loginLayout.setVisibility(View.GONE);
        controlLayout.setVisibility(View.VISIBLE);
        updateUIState();
    }

    private void updateUIState() {
        boolean permissionGranted = isOverlayPermissionGranted();

        if (permissionGranted) {
            btnRequestPermission.setVisibility(View.GONE);
            btnToggleService.setVisibility(View.VISIBLE);

            boolean running = isServiceRunning(OverlayService.class);
            if (running) {
                tvStatus.setText("NÚT NỔI HOẠT ĐỘNG");
                tvStatus.setTextColor(0xFF00FFFF); // Bright Cyan
                btnToggleService.setText("NÚT NỔI TẮT");
            } else {
                tvStatus.setText("NÚT NỔI đang TẮT");
                tvStatus.setTextColor(0xFFFF5555); // Red-pink
                btnToggleService.setText("BẬT NÚT NỔI");
            }
        } else {
            btnRequestPermission.setVisibility(View.VISIBLE);
            btnToggleService.setVisibility(View.GONE);
            tvStatus.setText("Chưa cấp quyền nút nổi trên ứng dụng khác!");
            tvStatus.setTextColor(0xFFFFAA00); // Orange
        }
    }

    private boolean isOverlayPermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true; // Granted automatically on pre-M devices
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
        }
    }

    private void toggleOverlayService() {
        Intent intent = new Intent(this, OverlayService.class);
        if (isServiceRunning(OverlayService.class)) {
            stopService(intent);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }
        // Give a tiny delay for service state to update before checking again
        new Handler().postDelayed(this::updateUIState, 300);
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (controlLayout.getVisibility() == View.VISIBLE) {
            updateUIState();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            new Handler().postDelayed(this::updateUIState, 500);
        }
    }
}
