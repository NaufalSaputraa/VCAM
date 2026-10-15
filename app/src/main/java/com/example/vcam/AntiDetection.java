package com.example.vcam;

import java.util.concurrent.ThreadLocalRandom;

public class AntiDetection {
    public static volatile boolean isFlashActive = false;

    /**
     * Applies subtle anti-detection processing to an NV21 frame.
     * 1. Flash simulation: Brightens luminance if camera flash/torch is triggered by eKYC.
     * 2. Natural noise: Injects tiny, imperceptible pixel variance into the Y plane to fool
     *    static frame / frozen software camera detection algorithms.
     */
    public static void processFrame(byte[] nv21, int width, int height, boolean addNoise, int noiseIntensity) {
        if (nv21 == null || width <= 0 || height <= 0) {
            return;
        }

        int ySize = width * height;
        if (nv21.length < ySize) {
            return;
        }

        // 1. Flash / Torch simulation
        if (isFlashActive) {
            for (int i = 0; i < ySize; i++) {
                int val = (nv21[i] & 0xFF) + 35;
                nv21[i] = (byte) (Math.min(val, 255));
            }
        }

        // 2. Micro-noise injection
        if (addNoise && noiseIntensity > 0) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            // Process every 4th pixel to keep CPU overhead under 0.1ms
            for (int i = 0; i < ySize; i += 4) {
                int delta = random.nextInt((noiseIntensity * 2) + 1) - noiseIntensity;
                int val = (nv21[i] & 0xFF) + delta;
                if (val < 0) {
                    val = 0;
                } else if (val > 255) {
                    val = 255;
                }
                nv21[i] = (byte) val;
            }
        }
    }
}
