package com.example.vcam;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class FloatingControllerService extends Service {
    private static final String CHANNEL_ID = "vcam_floating_channel";
    private static final int NOTIFICATION_ID = 1001;

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;

    private LinearLayout layoutBubble;
    private LinearLayout layoutExpanded;

    private Switch swEnable;
    private Switch swMirror;
    private Switch swLiveness;
    private Switch swMic;
    private Button btnPlayPause;
    private Button btnRotate;
    private Button btnCollapse;
    private Button btnStopService;
    private TextView tvBubbleIcon;

    private VCamConfig config;
    private String configDirPath;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundNotification();

        configDirPath = Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/";
        File dir = new File(configDirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        config = VCamConfig.loadFromDirectory(configDirPath);

        initFloatingView();
    }

    private void startForegroundNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "VCAM Floating Controller",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
                .setContentTitle("VCAM Virtual Camera")
                .setContentText("Kontrol melayang aktif")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initFloatingView() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_controller_layout, null);

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 200;

        layoutBubble = floatingView.findViewById(R.id.layout_bubble);
        layoutExpanded = floatingView.findViewById(R.id.layout_expanded);
        tvBubbleIcon = floatingView.findViewById(R.id.tv_bubble_icon);

        swEnable = floatingView.findViewById(R.id.sw_float_enable);
        swMirror = floatingView.findViewById(R.id.sw_float_mirror);
        swLiveness = floatingView.findViewById(R.id.sw_float_liveness);
        swMic = floatingView.findViewById(R.id.sw_float_mic);
        btnPlayPause = floatingView.findViewById(R.id.btn_float_play_pause);
        btnRotate = floatingView.findViewById(R.id.btn_float_rotate);
        btnCollapse = floatingView.findViewById(R.id.btn_collapse);
        btnStopService = floatingView.findViewById(R.id.btn_float_stop_service);

        syncUIWithConfig();

        // Dragging & Click Handling for Floating Bubble
        layoutBubble.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;

                    case MotionEvent.ACTION_UP:
                        int diffX = (int) Math.abs(event.getRawX() - initialTouchX);
                        int diffY = (int) Math.abs(event.getRawY() - initialTouchY);
                        if (diffX < 10 && diffY < 10) {
                            // Expand controller card
                            layoutBubble.setVisibility(View.GONE);
                            layoutExpanded.setVisibility(View.VISIBLE);
                        }
                        return true;
                }
                return false;
            }
        });

        btnCollapse.setOnClickListener(v -> {
            layoutExpanded.setVisibility(View.GONE);
            layoutBubble.setVisibility(View.VISIBLE);
        });

        swEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.enabled = isChecked;
            saveConfig();
            tvBubbleIcon.setText(isChecked ? "🎥" : "🚫");
            Toast.makeText(this, isChecked ? "Virtual Cam Aktif" : "Virtual Cam Dimatikan (Real Cam)", Toast.LENGTH_SHORT).show();
        });

        swMirror.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.mirror = isChecked;
            saveConfig();
        });

        swLiveness.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.antiDetection = isChecked;
            saveConfig();
            Toast.makeText(this, "Anti-eKYC: " + (isChecked ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
        });

        swMic.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.virtualMic = isChecked;
            config.micMode = isChecked ? "virtual" : "real";
            saveConfig();
            Toast.makeText(this, "Virtual Mic: " + (isChecked ? "Aktif" : "Real Mic"), Toast.LENGTH_SHORT).show();
        });

        btnPlayPause.setOnClickListener(v -> {
            config.playbackPaused = !config.playbackPaused;
            saveConfig();
            btnPlayPause.setText(config.playbackPaused ? "▶ Lanjutkan Video" : "⏸ Pause Video");
        });

        btnRotate.setOnClickListener(v -> {
            config.rotation = (config.rotation + 90) % 360;
            saveConfig();
            btnRotate.setText("🔄 Putar " + config.rotation + "°");
            Toast.makeText(this, "Rotasi: " + config.rotation + "°", Toast.LENGTH_SHORT).show();
        });

        btnStopService.setOnClickListener(v -> stopSelf());

        windowManager.addView(floatingView, params);
    }

    private void syncUIWithConfig() {
        swEnable.setChecked(config.enabled);
        swMirror.setChecked(config.mirror);
        swLiveness.setChecked(config.antiDetection);
        swMic.setChecked(config.virtualMic);
        btnPlayPause.setText(config.playbackPaused ? "▶ Lanjutkan Video" : "⏸ Pause Video");
        btnRotate.setText("🔄 Putar " + config.rotation + "°");
        tvBubbleIcon.setText(config.enabled ? "🎥" : "🚫");
    }

    private void saveConfig() {
        config.saveToDirectory(configDirPath);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
            floatingView = null;
        }
    }
}
