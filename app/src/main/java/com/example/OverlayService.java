package com.example;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OverlayService extends Service {

    private static final String CHANNEL_ID = "LagControlChannel";
    private static final int NOTIFICATION_ID = 4556;

    private WindowManager windowManager;
    private View floatingBubbleView;
    private View floatingPanelView;

    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams panelParams;

    private StringBuilder typedNumbers = new StringBuilder();
    private TextView tvDisplay;
    private OkHttpClient httpClient;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize OkHttpClient with reasonable timeouts
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .writeTimeout(8, TimeUnit.SECONDS)
                .build();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Create notification channel for Foreground Service
        createNotificationChannel();

        // Start as foreground service
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Lag TP×MDUC")
                .setContentText("Dịch vụ điều khiển đang chạy")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
        startForeground(NOTIFICATION_ID, notification);

        // Inflate and prepare the Floating Views
        initFloatingBubble();
        initFloatingPanel();

        // Add Bubble to Screen initially
        windowManager.addView(floatingBubbleView, bubbleParams);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Lag Control Service",
                    NotificationManager.IMPORTANCE_MIN
            );
            channel.setDescription("Kênh thông báo cho ứng dụng Lag TP×MDUC");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void initFloatingBubble() {
        floatingBubbleView = LayoutInflater.from(this).inflate(R.layout.floating_bubble, null);

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        bubbleParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = 150;
        bubbleParams.y = 300;

        // Implement smooth dragging and click detection
        floatingBubbleView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private long touchStartTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = bubbleParams.x;
                        initialY = bubbleParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchStartTime = System.currentTimeMillis();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        bubbleParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        bubbleParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        try {
                            windowManager.updateViewLayout(floatingBubbleView, bubbleParams);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        long clickDuration = System.currentTimeMillis() - touchStartTime;
                        float deltaX = event.getRawX() - initialTouchX;
                        float deltaY = event.getRawY() - initialTouchY;
                        double dragDistance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);

                        if (clickDuration < 200 && dragDistance < 10) {
                            // Click detected! Expand to control panel.
                            openControlPanel();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private int currentSizeMode = 1; // 0 = Small, 1 = Med, 2 = Large

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void applyPanelSize() {
        if (floatingPanelView == null || panelParams == null) return;

        TextView btnResize = floatingPanelView.findViewById(R.id.btn_resize);
        int widthDp;
        String label;

        switch (currentSizeMode) {
            case 0:
                widthDp = 220;
                label = "S";
                break;
            case 1:
            default:
                widthDp = 280;
                label = "M";
                break;
            case 2:
                widthDp = 340;
                label = "L";
                break;
        }

        if (btnResize != null) {
            btnResize.setText(label);
        }

        panelParams.width = dpToPx(widthDp);

        try {
            if (floatingPanelView.getParent() != null) {
                windowManager.updateViewLayout(floatingPanelView, panelParams);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initFloatingPanel() {
        floatingPanelView = LayoutInflater.from(this).inflate(R.layout.floating_panel, null);

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        panelParams = new WindowManager.LayoutParams(
                dpToPx(280), // Start with medium size (280dp)
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        panelParams.gravity = Gravity.CENTER;

        // Make the Panel draggable by its header
        View panelHeader = floatingPanelView.findViewById(R.id.panel_header);
        if (panelHeader != null) {
            panelHeader.setOnTouchListener(new View.OnTouchListener() {
                private int initialX;
                private int initialY;
                private float initialTouchX;
                private float initialTouchY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = panelParams.x;
                            initialY = panelParams.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            panelParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                            panelParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                            try {
                                windowManager.updateViewLayout(floatingPanelView, panelParams);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            return true;
                    }
                    return false;
                }
            });
        }

        // Display Screen inside Panel
        tvDisplay = floatingPanelView.findViewById(R.id.tv_display);
        updateDisplay();

        // Setup Panel Keyboard Buttons
        setupKeyboardKeys();

        // Header controls (Resize, Minimize & Close)
        TextView btnResize = floatingPanelView.findViewById(R.id.btn_resize);
        if (btnResize != null) {
            btnResize.setText("M");
            btnResize.setOnClickListener(v -> {
                currentSizeMode = (currentSizeMode + 1) % 3;
                applyPanelSize();
            });
        }

        floatingPanelView.findViewById(R.id.btn_minimize).setOnClickListener(v -> minimizePanel());
        floatingPanelView.findViewById(R.id.btn_close_panel).setOnClickListener(v -> closeOverlayCompletely());

        // OK / Submit Request Button
        Button btnSubmit = floatingPanelView.findViewById(R.id.btn_submit);
        btnSubmit.setOnClickListener(v -> handleFormSubmission());
    }

    private void setupKeyboardKeys() {
        int[] keyIds = {
                R.id.key_0, R.id.key_1, R.id.key_2, R.id.key_3,
                R.id.key_4, R.id.key_5, R.id.key_6, R.id.key_7,
                R.id.key_8, R.id.key_9
        };

        for (int id : keyIds) {
            TextView key = floatingPanelView.findViewById(id);
            if (key != null) {
                final String text = key.getText().toString();
                key.setOnClickListener(v -> {
                    if (typedNumbers.length() < 7) {
                        typedNumbers.append(text);
                        updateDisplay();
                    }
                });
            }
        }

        // Clear Key
        View keyClear = floatingPanelView.findViewById(R.id.key_clear);
        if (keyClear != null) {
            keyClear.setOnClickListener(v -> {
                typedNumbers.setLength(0);
                updateDisplay();
            });
        }

        // Delete Key
        View keyDel = floatingPanelView.findViewById(R.id.key_del);
        if (keyDel != null) {
            keyDel.setOnClickListener(v -> {
                if (typedNumbers.length() > 0) {
                    typedNumbers.setLength(typedNumbers.length() - 1);
                    updateDisplay();
                }
            });
        }
    }

    private void updateDisplay() {
        if (tvDisplay == null) return;

        if (typedNumbers.length() == 0) {
            tvDisplay.setText("_______");
            tvDisplay.setTextColor(0x66FFFFFF); // Semi-transparent
        } else {
            // Build visual representation
            StringBuilder visual = new StringBuilder();
            for (int i = 0; i < 7; i++) {
                if (i < typedNumbers.length()) {
                    visual.append(typedNumbers.charAt(i));
                } else {
                    visual.append("_");
                }
                if (i < 6) visual.append(" ");
            }
            tvDisplay.setText(visual.toString());
            tvDisplay.setTextColor(0xFFFFFFFF); // Solid White
        }
    }

    private void openControlPanel() {
        try {
            // Hide Chat Head Bubble
            floatingBubbleView.setVisibility(View.GONE);

            // Add panel view if not already added, or make it visible
            if (floatingPanelView.getParent() == null) {
                windowManager.addView(floatingPanelView, panelParams);
            } else {
                floatingPanelView.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void minimizePanel() {
        try {
            if (floatingPanelView != null && floatingPanelView.getParent() != null) {
                floatingPanelView.setVisibility(View.GONE);
            }
            if (floatingBubbleView != null) {
                floatingBubbleView.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void closeOverlayCompletely() {
        stopSelf();
    }

    private void handleFormSubmission() {
        if (typedNumbers.length() != 7) {
            Toast.makeText(this, "Vui lòng nhập đúng 7 chữ số", Toast.LENGTH_SHORT).show();
            return;
        }

        String inputDigits = typedNumbers.toString();
        // Construct target URL: http://192.168.1.8:2081/kick?tc={1234567}
        String targetUrl = "http://192.168.1.8:2081/kick?tc={" + inputDigits + "}";

        // Send HTTP GET Request asynchronously
        Request request = new Request.Builder()
                .url(targetUrl)
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(OverlayService.this, "Không kết nối được máy chủ", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(OverlayService.this, "Đã gửi thành công", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(OverlayService.this, "Không kết nối được máy chủ", Toast.LENGTH_SHORT).show();
                    }
                    response.close();
                });
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Clean up all overlay views from the Window Manager
        try {
            if (windowManager != null) {
                if (floatingBubbleView != null && floatingBubbleView.getParent() != null) {
                    windowManager.removeView(floatingBubbleView);
                }
                if (floatingPanelView != null && floatingPanelView.getParent() != null) {
                    windowManager.removeView(floatingPanelView);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
