# VCAM - Android Virtual Camera & Microphone Module

[![Platform](https://img.shields.io/badge/Platform-Android%207.0%20--%2016%20(API%2024--36)-brightgreen.svg)](https://github.com/NaufalSaputraa/VCAM)
[![Framework](https://img.shields.io/badge/Xposed-LSPosed%20%7C%20EdXposed-blue.svg)](https://github.com/NaufalSaputraa/VCAM)
[![Version](https://img.shields.io/badge/Version-5.0%20(Modernized)-orange.svg)](https://github.com/NaufalSaputraa/VCAM/releases)
[![License](https://img.shields.io/badge/License-MIT-purple.svg)](./LICENSE)

**VCAM** is a powerful Xposed / LSPosed module for Android that replaces your physical camera preview and microphone input with your own custom videos and photos.

---

## ⚠️ Disclaimer

> [!WARNING]
> This project is created strictly for **educational, security research, and app testing purposes only**. 
> Do **NOT** use this tool for fraud, impersonation, or any illegal activities. You are solely responsible for how you use this software.

---

## ✨ Features

- 🎥 **Virtual Camera Feed**: Automatically replaces live camera previews in target apps with your custom `virtual.mp4` video.
- 📸 **Virtual Photo Capture**: Replaces photo capture with custom BMP images (supports auto-conversion from JPG/PNG).
- 🎙️ **Virtual Microphone Injection**: Plays audio from your virtual video directly into apps requesting microphone access (`AudioRecord`), complete with dynamic resampling (16kHz / 44.1kHz / 48kHz, mono & stereo) so voices sound natural without pitch or speed distortion.
- 🔄 **Rotation & Aspect Ratio Fit**: Rotate video by `0°`, `90°`, `180°`, or `270°` with automatic letterboxing (black bars) or center-crop to prevent image stretching or diagonal skewing.
- 🪞 **Mirror Flip Mode**: Horizontal flip option for front-facing / selfie cameras.
- 🎛️ **Floating Controller Overlay**: A movable on-screen bubble allowing you to toggle virtual/real camera, rotate, flip, pause/play video, or switch mic modes in real-time without leaving your target app!
- 🛡️ **Anti-Detection (eKYC Liveness Bypass)**: Adds subtle micro-noise to the camera luma plane to defeat static-frame checks and simulates flash/torch response.
- 📂 **Built-in In-App File Picker**: Pick videos and images directly from your gallery inside the app — no need for manual file managers or renaming.
- ⚡ **Zero-Lag Architecture**: Uses an in-memory configuration cache (`vcam_config.json`) instead of repetitive disk reading, preventing frame drops.
- 📱 **Camera1 & Camera2 Support**: Full compatibility with both legacy Camera1 API and modern Camera2 API.

---

## 📋 Requirements

1. A rooted Android device (Android 7.0 up to Android 15/16).
2. An active Xposed framework (e.g., **LSPosed**, EdXposed, or KernelSU/APatch Zygisk-LSPosed).

---

## 🚀 Quick Start Guide

### Step 1: Install & Enable the Module
1. Download the latest APK from the [Releases](https://github.com/NaufalSaputraa/VCAM/releases) page or build it using `./gradlew assembleDebug`.
2. Install the APK on your device.
3. Open your Xposed manager (e.g. **LSPosed**):
   - Enable the **VCAM** module.
   - In the **Scope** section, check the target apps where you want the virtual camera to work (e.g., Camera app, WhatsApp, Zoom, test app).
   - *Note: Do NOT select System Framework.*
4. Restart your target app (or reboot your device).

### Step 2: Grant Storage Permission
1. Open the **VCAM** app on your phone.
2. Tap **"Grant Full Storage Access"** and enable **"All files access"** (required for Android 11+).
3. If using the floating bubble, allow **"Display over other apps"** when prompted.

### Step 3: Pick Your Replacement Video & Photo
1. In the VCAM app, tap **"Choose & Set Replacement Video"** to pick any `.mp4` file from your device.
2. *(Optional)* Tap **"Choose & Set Replacement Image"** to pick a picture for snapshot capture (automatically converted to 24-bit BMP).
3. Set your desired **Rotation**, **Mirror mode**, and **Virtual Audio** toggles.

### Step 4: Open Your Target App
- Open your target app and start the camera.
- Your replacement video will now play seamlessly as the camera feed!

---

## 🎛️ Using the Floating Controller

Tap **"Open Floating Controller Overlay"** inside the VCAM app:
- A small floating camera icon will appear over your screen.
- **Drag** it anywhere along the edge of your screen.
- **Tap** it to open quick controls:
  - 🎥 **Virtual Cam**: Toggle between your virtual video and your real physical camera.
  - 🔄 **Rotate**: Rotate the feed by 90° increments instantly.
  - ⏸ **Pause/Play**: Freeze or resume video playback.
  - 🪞 **Mirror**: Flip between mirror and normal mode.
  - 🎙️ **Mic**: Toggle virtual microphone on/off.

---

## 📁 File Storage Paths

VCAM looks for replacement media in the following directories:

| Mode | Storage Location | Description |
|---|---|---|
| **Shared (Default)** | `/sdcard/DCIM/Camera1/` | Shared across all target apps |
| **Private (App-specific)** | `/sdcard/Android/data/<package_name>/files/Camera1/` | Applies only to that specific app |

### Key Files in `Camera1/`:
- `virtual.mp4`: The replacement video file.
- `virtual.bmp` (or `1000.bmp`): The replacement photo file.
- `vcam_config.json`: Centralized settings generated automatically by the app.

---

## ❓ Troubleshooting & FAQ

### 1. The target app shows a black screen or real camera:
- Make sure the target app is ticked in **LSPosed Scope**.
- Force-stop the target app and open it again.
- Verify that `virtual.mp4` exists in `/sdcard/DCIM/Camera1/`.

### 2. The video is rotated or looks stretched:
- Open VCAM and select the matching **Rotation** (`90°`, `180°`, or `270°`).
- Select **Aspect Ratio Scaling**:
  - `Fit (Letterbox)`: Keeps original aspect ratio with black bars.
  - `Fill (Crop)`: Fills entire screen by cropping center.
  - `Stretch`: Stretches video to fill camera dimensions.

### 3. Audio sounds distorted or fast/slow:
- VCAM v5.0 automatically detects the target app's requested sample rate and resamples audio. Ensure **Virtual Audio Injection** switch is turned ON.

---

## 🛠️ Building from Source

```bash
# Clone the repository
git clone https://github.com/NaufalSaputraa/VCAM.git
cd VCAM

# Build Debug APK
./gradlew assembleDebug

# Output APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📜 Credits & License

- Original implementation by [@w2016561536](https://github.com/w2016561536/android_virtual_cam) & [@Cross2pro](https://github.com/Cross2pro/android_VCAM-Revise).
- Enhanced and modernized by [@NaufalSaputraa](https://github.com/NaufalSaputraa).
- Licensed under the [MIT License](./LICENSE).
