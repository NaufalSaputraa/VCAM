package com.example.vcam;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String TAG = "VCAM_Main";
    private static final int REQUEST_STORAGE_PERMISSIONS = 101;
    private static final int REQUEST_PICK_VIDEO = 201;
    private static final int REQUEST_PICK_IMAGE = 202;

    private Switch force_show_switch;
    private Switch disable_switch;
    private Switch play_sound_switch;
    private Switch force_private_dir;
    private Switch disable_toast_switch;

    private Spinner spinner_rotation;
    private Spinner spinner_scale_mode;
    private Switch switch_mirror;
    private Switch switch_virtual_mic;
    private Switch switch_anti_ekyc;
    private Button btn_toggle_floating;

    private TextView tv_media_dir;
    private TextView tv_video_status;
    private Button btn_grant_storage;
    private Button btn_pick_video;
    private Button btn_pick_image;

    private VCamConfig currentConfig;
    private boolean isUpdatingUi = false;

    private File getCameraDir() {
        return new File(Environment.getExternalStorageDirectory(), "DCIM/Camera1");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button repo_button = findViewById(R.id.button);
        Button repo_button_chinamainland = findViewById(R.id.button2);

        force_show_switch = findViewById(R.id.switch1);
        disable_switch = findViewById(R.id.switch2);
        play_sound_switch = findViewById(R.id.switch3);
        force_private_dir = findViewById(R.id.switch4);
        disable_toast_switch = findViewById(R.id.switch5);

        spinner_rotation = findViewById(R.id.spinner_rotation);
        spinner_scale_mode = findViewById(R.id.spinner_scale_mode);
        switch_mirror = findViewById(R.id.switch_mirror);
        switch_virtual_mic = findViewById(R.id.switch_virtual_mic);
        switch_anti_ekyc = findViewById(R.id.switch_anti_ekyc);
        btn_toggle_floating = findViewById(R.id.btn_toggle_floating);

        tv_media_dir = findViewById(R.id.tv_media_dir);
        tv_video_status = findViewById(R.id.tv_video_status);
        btn_grant_storage = findViewById(R.id.btn_grant_storage);
        btn_pick_video = findViewById(R.id.btn_pick_video);
        btn_pick_image = findViewById(R.id.btn_pick_image);

        setupEventListeners(repo_button, repo_button_chinamainland);
        sync_status_with_files();
    }

    @Override
    protected void onResume() {
        super.onResume();
        sync_status_with_files();
    }

    private void setupEventListeners(Button repo_button, Button repo_chinamainland) {
        repo_button.setOnClickListener(v -> {
            Uri uri = Uri.parse("https://github.com/Cross2pro/android_VCAM-Revise");
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        });

        repo_chinamainland.setOnClickListener(v -> {
            Uri uri = Uri.parse("https://gitee.com/w2016561536/android_virtual_cam");
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        });

        btn_grant_storage.setOnClickListener(v -> request_storage_permission());

        btn_pick_video.setOnClickListener(v -> {
            if (!has_storage_permission()) {
                request_storage_permission();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("video/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, getString(R.string.btn_pick_video)), REQUEST_PICK_VIDEO);
        });

        btn_pick_image.setOnClickListener(v -> {
            if (!has_storage_permission()) {
                request_storage_permission();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, getString(R.string.btn_pick_image)), REQUEST_PICK_IMAGE);
        });

        disable_switch.setOnCheckedChangeListener((cb, isChecked) -> {
            if (!isUpdatingUi && cb.isPressed()) {
                currentConfig.enabled = !isChecked; // switch is "disable temporarily"
                saveConfig();
            }
        });

        force_show_switch.setOnCheckedChangeListener((cb, isChecked) -> {
            if (!isUpdatingUi && cb.isPressed()) {
                currentConfig.forceShow = isChecked;
                saveConfig();
            }
        });

        play_sound_switch.setOnCheckedChangeListener((cb, isChecked) -> {
            if (!isUpdatingUi && cb.isPressed()) {
                currentConfig.playSound = isChecked;
                saveConfig();
            }
        });

        force_private_dir.setOnCheckedChangeListener((cb, isChecked) -> {
            if (!isUpdatingUi && cb.isPressed()) {
                currentConfig.forcePrivateDir = isChecked;
                saveConfig();
            }
        });

        disable_toast_switch.setOnCheckedChangeListener((cb, isChecked) -> {
            if (!isUpdatingUi && cb.isPressed()) {
                currentConfig.showToast = !isChecked; // switch is "disable toast"
                saveConfig();
            }
        });

        switch_mirror.setOnCheckedChangeListener((cb, isChecked) -> {
            if (!isUpdatingUi && cb.isPressed()) {
                currentConfig.mirror = isChecked;
                saveConfig();
            }
        });

        switch_virtual_mic.setOnCheckedChangeListener((cb, isChecked) -> {
            if (!isUpdatingUi && cb.isPressed()) {
                currentConfig.virtualMic = isChecked;
                saveConfig();
            }
        });

        switch_anti_ekyc.setOnCheckedChangeListener((cb, isChecked) -> {
            if (!isUpdatingUi && cb.isPressed()) {
                currentConfig.antiDetection = isChecked;
                saveConfig();
            }
        });

        btn_toggle_floating.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Izinkan izin Tampilkan di Atas Aplikasi Lain (Display over other apps)", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                    return;
                }
            }
            Intent serviceIntent = new Intent(this, FloatingControllerService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Toast.makeText(this, "Kontrol melayang diaktifkan", Toast.LENGTH_SHORT).show();
        });

        spinner_rotation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingUi) return;
                int[] degrees = {0, 90, 180, 270};
                if (position >= 0 && position < degrees.length) {
                    currentConfig.rotation = degrees[position];
                    saveConfig();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinner_scale_mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingUi) return;
                String[] modes = {"fit", "fill", "stretch"};
                if (position >= 0 && position < modes.length) {
                    currentConfig.scaleMode = modes[position];
                    saveConfig();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void saveConfig() {
        if (!has_storage_permission()) {
            request_storage_permission();
            return;
        }
        File cameraDir = getCameraDir();
        if (currentConfig != null) {
            currentConfig.saveToDirectory(cameraDir.getAbsolutePath());
        }
    }

    private boolean has_storage_permission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void request_storage_permission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_STORAGE_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show();
                sync_status_with_files();
            } else {
                Toast.makeText(this, R.string.permission_lack_warn, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();
        File cameraDir = getCameraDir();
        if (!cameraDir.exists()) {
            cameraDir.mkdirs();
        }

        if (requestCode == REQUEST_PICK_VIDEO) {
            File targetVideo = new File(cameraDir, "virtual.mp4");
            copyUriToFile(uri, targetVideo);
        } else if (requestCode == REQUEST_PICK_IMAGE) {
            File targetBmp = new File(cameraDir, "virtual.bmp");
            convertUriToBmp(uri, targetBmp);
        }
        sync_status_with_files();
    }

    private void copyUriToFile(Uri sourceUri, File destFile) {
        try (InputStream in = getContentResolver().openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(destFile)) {
            if (in == null) throw new IOException("Cannot open input stream");
            byte[] buf = new byte[64 * 1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.flush();
            Toast.makeText(this, R.string.video_imported_success, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error copying video", e);
            Toast.makeText(this, String.format(getString(R.string.import_failed), e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void convertUriToBmp(Uri sourceUri, File destFile) {
        try (InputStream in = getContentResolver().openInputStream(sourceUri)) {
            if (in == null) throw new IOException("Cannot open image stream");
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            if (bitmap == null) throw new IOException("Failed to decode image");

            writeBitmapAsBmp(bitmap, destFile);
            bitmap.recycle();
            Toast.makeText(this, R.string.image_imported_success, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error converting image to BMP", e);
            Toast.makeText(this, String.format(getString(R.string.import_failed), e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void writeBitmapAsBmp(Bitmap bitmap, File file) throws IOException {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int rowStride = (width * 3 + 3) & ~3;
        int imageSize = rowStride * height;
        int fileSize = 54 + imageSize;

        ByteBuffer buffer = ByteBuffer.allocate(54);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // BMP Header
        buffer.put((byte) 'B');
        buffer.put((byte) 'M');
        buffer.putInt(fileSize);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);
        buffer.putInt(54); // pixel data offset

        // DIB Header (BITMAPINFOHEADER)
        buffer.putInt(40); // header size
        buffer.putInt(width);
        buffer.putInt(height); // bottom-up
        buffer.putShort((short) 1); // planes
        buffer.putShort((short) 24); // bpp
        buffer.putInt(0); // BI_RGB (uncompressed)
        buffer.putInt(imageSize);
        buffer.putInt(2835); // horizontal resolution (72 DPI)
        buffer.putInt(2835); // vertical resolution
        buffer.putInt(0);
        buffer.putInt(0);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(buffer.array());

            int[] pixels = new int[width];
            byte[] rowData = new byte[rowStride];

            // Bottom-up order
            for (int y = height - 1; y >= 0; y--) {
                bitmap.getPixels(pixels, 0, width, 0, y, width, 1);
                int rowIdx = 0;
                for (int x = 0; x < width; x++) {
                    int pixel = pixels[x];
                    rowData[rowIdx++] = (byte) (pixel & 0xFF);         // Blue
                    rowData[rowIdx++] = (byte) ((pixel >> 8) & 0xFF);  // Green
                    rowData[rowIdx++] = (byte) ((pixel >> 16) & 0xFF); // Red
                }
                while (rowIdx < rowStride) {
                    rowData[rowIdx++] = 0; // padding
                }
                fos.write(rowData);
            }
            fos.flush();
        }
    }

    private void sync_status_with_files() {
        File cameraDir = getCameraDir();
        boolean hasPermission = has_storage_permission();

        if (btn_grant_storage != null) {
            btn_grant_storage.setVisibility(hasPermission ? View.GONE : View.VISIBLE);
        }

        if (hasPermission && !cameraDir.exists()) {
            cameraDir.mkdirs();
        }

        if (tv_media_dir != null) {
            tv_media_dir.setText(String.format(getString(R.string.media_path_label), cameraDir.getAbsolutePath()));
        }

        File videoFile = new File(cameraDir, "virtual.mp4");
        if (tv_video_status != null) {
            if (videoFile.exists() && videoFile.length() > 0) {
                long sizeKb = videoFile.length() / 1024;
                String sizeStr = (sizeKb > 1024) ? String.format(Locale.getDefault(), "%.1f MB", sizeKb / 1024.0) : sizeKb + " KB";
                tv_video_status.setText(String.format(getString(R.string.video_status_found), sizeStr));
                tv_video_status.setTextColor(0xFF2E7D32); // Green
            } else {
                tv_video_status.setText(R.string.video_status_not_found);
                tv_video_status.setTextColor(0xFFD32F2F); // Red
            }
        }

        currentConfig = VCamConfig.loadFromDirectory(cameraDir.getAbsolutePath());

        isUpdatingUi = true;
        try {
            disable_switch.setChecked(!currentConfig.enabled);
            force_show_switch.setChecked(currentConfig.forceShow);
            play_sound_switch.setChecked(currentConfig.playSound);
            force_private_dir.setChecked(currentConfig.forcePrivateDir);
            disable_toast_switch.setChecked(!currentConfig.showToast);
            switch_mirror.setChecked(currentConfig.mirror);
            switch_virtual_mic.setChecked(currentConfig.virtualMic);
            switch_anti_ekyc.setChecked(currentConfig.antiDetection);

            // Set rotation spinner
            int rotPos = 0;
            switch (currentConfig.rotation) {
                case 90: rotPos = 1; break;
                case 180: rotPos = 2; break;
                case 270: rotPos = 3; break;
                default: rotPos = 0; break;
            }
            spinner_rotation.setSelection(rotPos);

            // Set scale mode spinner
            int scalePos = 0;
            if ("fill".equalsIgnoreCase(currentConfig.scaleMode)) {
                scalePos = 1;
            } else if ("stretch".equalsIgnoreCase(currentConfig.scaleMode)) {
                scalePos = 2;
            }
            spinner_scale_mode.setSelection(scalePos);
        } finally {
            isUpdatingUi = false;
        }
    }
}
