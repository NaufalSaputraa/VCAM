package com.example.vcam;

import android.os.Environment;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class VCamConfig {
    private static final String TAG = "VCamConfig";
    public static final String CONFIG_FILE_NAME = "vcam_config.json";

    public boolean enabled = true;
    public boolean playSound = false;
    public boolean forcePrivateDir = false;
    public boolean showToast = true;
    public boolean forceShow = false;
    public int rotation = 0; // 0, 90, 180, 270
    public boolean mirror = false;
    public String scaleMode = "fit"; // "fit", "fill", "stretch"
    public boolean virtualMic = false;
    public String micMode = "virtual"; // "virtual", "mute", "real"
    public boolean antiDetection = true;
    public int noiseIntensity = 2; // 0 to 5
    public boolean simulateFlash = true;
    public boolean playbackPaused = false;
    public String videoFileName = "virtual.mp4";
    public String imageFileName = "virtual.bmp";

    private long lastLoadedTime = 0;
    private long lastFileModified = 0;

    public static VCamConfig loadFromDirectory(String dirPath) {
        VCamConfig config = new VCamConfig();
        File configFile = new File(dirPath, CONFIG_FILE_NAME);

        if (configFile.exists() && configFile.canRead()) {
            try {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }
                JSONObject json = new JSONObject(sb.toString());
                config.enabled = json.optBoolean("enabled", true);
                config.playSound = json.optBoolean("playSound", false);
                config.forcePrivateDir = json.optBoolean("forcePrivateDir", false);
                config.showToast = json.optBoolean("showToast", true);
                config.forceShow = json.optBoolean("forceShow", false);
                config.rotation = json.optInt("rotation", 0);
                config.mirror = json.optBoolean("mirror", false);
                config.scaleMode = json.optString("scaleMode", "fit");
                config.virtualMic = json.optBoolean("virtualMic", false);
                config.micMode = json.optString("micMode", config.virtualMic ? "virtual" : "real");
                config.antiDetection = json.optBoolean("antiDetection", true);
                config.noiseIntensity = json.optInt("noiseIntensity", 2);
                config.simulateFlash = json.optBoolean("simulateFlash", true);
                config.playbackPaused = json.optBoolean("playbackPaused", false);
                config.videoFileName = json.optString("videoFileName", "virtual.mp4");
                config.imageFileName = json.optString("imageFileName", "virtual.bmp");
                config.lastFileModified = configFile.lastModified();
                config.lastLoadedTime = System.currentTimeMillis();
                return config;
            } catch (Exception e) {
                Log.e(TAG, "Error parsing config json, falling back to legacy checks", e);
            }
        }

        // Fallback to legacy .jpg flags
        config.enabled = !(new File(dirPath, "disable.jpg").exists());
        config.playSound = (new File(dirPath, "no-silent.jpg").exists());
        config.forcePrivateDir = (new File(dirPath, "private_dir.jpg").exists());
        config.showToast = !(new File(dirPath, "no_toast.jpg").exists());
        config.forceShow = (new File(dirPath, "force_show.jpg").exists());
        config.lastLoadedTime = System.currentTimeMillis();
        return config;
    }

    public boolean saveToDirectory(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File configFile = new File(dir, CONFIG_FILE_NAME);
        try {
            JSONObject json = new JSONObject();
            json.put("enabled", enabled);
            json.put("playSound", playSound);
            json.put("forcePrivateDir", forcePrivateDir);
            json.put("showToast", showToast);
            json.put("forceShow", forceShow);
            json.put("rotation", rotation);
            json.put("mirror", mirror);
            json.put("scaleMode", scaleMode);
            json.put("virtualMic", virtualMic);
            json.put("micMode", micMode);
            json.put("antiDetection", antiDetection);
            json.put("noiseIntensity", noiseIntensity);
            json.put("simulateFlash", simulateFlash);
            json.put("playbackPaused", playbackPaused);
            json.put("videoFileName", videoFileName);
            json.put("imageFileName", imageFileName);

            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                fos.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }

            // Sync with legacy .jpg flags for full backward compatibility
            syncLegacyFlag(dir, "disable.jpg", !enabled);
            syncLegacyFlag(dir, "no-silent.jpg", playSound);
            syncLegacyFlag(dir, "private_dir.jpg", forcePrivateDir);
            syncLegacyFlag(dir, "no_toast.jpg", !showToast);
            syncLegacyFlag(dir, "force_show.jpg", forceShow);

            lastFileModified = configFile.lastModified();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error saving config", e);
            return false;
        }
    }

    private void syncLegacyFlag(File dir, String filename, boolean shouldExist) {
        File flagFile = new File(dir, filename);
        try {
            if (shouldExist && !flagFile.exists()) {
                flagFile.createNewFile();
            } else if (!shouldExist && flagFile.exists()) {
                flagFile.delete();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to sync legacy flag " + filename, e);
        }
    }

    public boolean needsReload(String dirPath) {
        File configFile = new File(dirPath, CONFIG_FILE_NAME);
        if (configFile.exists()) {
            return configFile.lastModified() != lastFileModified;
        }
        // Fallback: reload every 3 seconds if only using legacy files
        return System.currentTimeMillis() - lastLoadedTime > 3000;
    }
}
