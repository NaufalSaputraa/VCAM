package com.example.vcam;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import de.robv.android.xposed.XposedBridge;

/**
 * High-fidelity virtual microphone injector with dynamic resampling and channel conversion.
 * Resamples source audio (e.g. 44.1kHz / 48kHz stereo) into target AudioRecord parameters
 * (e.g. 16kHz mono, 44.1kHz stereo) with linear interpolation to prevent pitch/speed distortion.
 */
public class AudioInjector {
    private static final String TAG = "AudioInjector";

    private static volatile short[] sourceShorts = null;
    private static volatile int sourceSampleRate = 44100;
    private static volatile int sourceChannels = 2;
    private static volatile double playbackFrameIndex = 0.0;

    private static volatile String currentLoadedPath = null;
    private static volatile boolean isLoading = false;

    /**
     * Prepares and decodes the audio track from the video file into raw PCM 16-bit in the background.
     */
    public static synchronized void prepareAudio(String filePath) {
        if (filePath == null || filePath.equals(currentLoadedPath)) {
            return;
        }
        if (isLoading) {
            return;
        }

        File file = new File(filePath);
        if (!file.exists() || !file.canRead()) {
            sourceShorts = null;
            currentLoadedPath = null;
            return;
        }

        isLoading = true;
        new Thread(() -> {
            MediaExtractor extractor = null;
            MediaCodec codec = null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream(1024 * 512);

            try {
                extractor = new MediaExtractor();
                extractor.setDataSource(filePath);

                int audioTrackIndex = -1;
                MediaFormat audioFormat = null;
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    MediaFormat format = extractor.getTrackFormat(i);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("audio/")) {
                        audioTrackIndex = i;
                        audioFormat = format;
                        break;
                    }
                }

                if (audioTrackIndex < 0) {
                    XposedBridge.log("【VCAM-Audio】No audio track found in " + filePath);
                    sourceShorts = null;
                    currentLoadedPath = filePath;
                    isLoading = false;
                    return;
                }

                int sRate = audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE) ?
                        audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
                int channels = audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ?
                        audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;

                extractor.selectTrack(audioTrackIndex);
                String mime = audioFormat.getString(MediaFormat.KEY_MIME);
                codec = MediaCodec.createDecoderByType(mime);
                codec.configure(audioFormat, null, null, 0);
                codec.start();

                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                boolean isEOS = false;
                long timeoutUs = 10000;

                while (!isEOS) {
                    int inIndex = codec.dequeueInputBuffer(timeoutUs);
                    if (inIndex >= 0) {
                        ByteBuffer inputBuffer;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            inputBuffer = codec.getInputBuffer(inIndex);
                        } else {
                            inputBuffer = codec.getInputBuffers()[inIndex];
                        }

                        if (inputBuffer != null) {
                            inputBuffer.clear();
                            int sampleSize = extractor.readSampleData(inputBuffer, 0);
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                isEOS = true;
                            } else {
                                codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                                extractor.advance();
                            }
                        }
                    }

                    int outIndex = codec.dequeueOutputBuffer(info, timeoutUs);
                    while (outIndex >= 0) {
                        ByteBuffer outputBuffer;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            outputBuffer = codec.getOutputBuffer(outIndex);
                        } else {
                            outputBuffer = codec.getOutputBuffers()[outIndex];
                        }

                        if (outputBuffer != null && info.size > 0) {
                            outputBuffer.position(info.offset);
                            outputBuffer.limit(info.offset + info.size);
                            byte[] chunk = new byte[info.size];
                            outputBuffer.get(chunk);
                            bos.write(chunk);
                        }
                        codec.releaseOutputBuffer(outIndex, false);
                        outIndex = codec.dequeueOutputBuffer(info, timeoutUs);
                    }
                }

                byte[] rawBytes = bos.toByteArray();
                if (rawBytes.length >= 2) {
                    short[] decodedShorts = new short[rawBytes.length / 2];
                    ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(decodedShorts);

                    synchronized (AudioInjector.class) {
                        sourceShorts = decodedShorts;
                        sourceSampleRate = (sRate > 0) ? sRate : 44100;
                        sourceChannels = (channels > 0) ? channels : 2;
                        playbackFrameIndex = 0.0;
                        currentLoadedPath = filePath;
                    }
                    XposedBridge.log("【VCAM-Audio】Audio track decoded: " + decodedShorts.length + " samples ("
                            + sourceSampleRate + "Hz, " + sourceChannels + "ch)");
                } else {
                    sourceShorts = null;
                }
            } catch (Throwable t) {
                XposedBridge.log("【VCAM-Audio】Error decoding audio track: " + t.getMessage());
                sourceShorts = null;
            } finally {
                isLoading = false;
                if (codec != null) {
                    try {
                        codec.stop();
                        codec.release();
                    } catch (Throwable ignored) {}
                }
                if (extractor != null) {
                    try {
                        extractor.release();
                    } catch (Throwable ignored) {}
                }
            }
        }, "VCAM-AudioDecoder").start();
    }

    /**
     * Reads PCM bytes with dynamic resampling and channel conversion.
     */
    public static synchronized void readBytes(byte[] target, int offset, int size, int targetSampleRate, int targetChannels) {
        if (target == null || size <= 0 || offset < 0 || offset + size > target.length) {
            return;
        }

        short[] src = sourceShorts;
        if (src == null || src.length == 0) {
            Arrays.fill(target, offset, offset + size, (byte) 0);
            return;
        }

        int targetShortCount = size / 2;
        short[] tempShorts = new short[targetShortCount];
        readShorts(tempShorts, 0, targetShortCount, targetSampleRate, targetChannels);

        ByteBuffer byteBuf = ByteBuffer.wrap(target, offset, size).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < targetShortCount; i++) {
            byteBuf.putShort(tempShorts[i]);
        }
    }

    public static void readBytes(byte[] target, int offset, int size) {
        readBytes(target, offset, size, 16000, 1);
    }

    /**
     * Reads PCM 16-bit shorts with dynamic linear interpolation resampling and channel downmix/upmix.
     */
    public static synchronized void readShorts(short[] target, int offset, int size, int targetSampleRate, int targetChannels) {
        if (target == null || size <= 0 || offset < 0 || offset + size > target.length) {
            return;
        }

        short[] src = sourceShorts;
        if (src == null || src.length == 0) {
            Arrays.fill(target, offset, offset + size, (short) 0);
            return;
        }

        int srcCh = sourceChannels;
        int tgtCh = (targetChannels > 0) ? targetChannels : 1;
        int srcSR = (sourceSampleRate > 0) ? sourceSampleRate : 44100;
        int tgtSR = (targetSampleRate > 0) ? targetSampleRate : 16000;

        int totalSrcFrames = src.length / srcCh;
        if (totalSrcFrames <= 1) {
            Arrays.fill(target, offset, offset + size, (short) 0);
            return;
        }

        double frameStep = (double) srcSR / (double) tgtSR;
        double currentFrame = playbackFrameIndex;

        int targetFrames = size / tgtCh;
        int outIndex = offset;

        for (int f = 0; f < targetFrames; f++) {
            int frame0 = (int) currentFrame;
            int frame1 = (frame0 + 1) % totalSrcFrames;
            double frac = currentFrame - frame0;

            if (srcCh == 1 && tgtCh == 1) {
                short s0 = src[frame0];
                short s1 = src[frame1];
                target[outIndex++] = (short) (s0 + frac * (s1 - s0));
            } else if (srcCh == 2 && tgtCh == 1) {
                // Stereo to Mono downmix (average L + R)
                int idx0 = frame0 * 2;
                int idx1 = frame1 * 2;
                int mono0 = (src[idx0] + src[idx0 + 1]) / 2;
                int mono1 = (src[idx1] + src[idx1 + 1]) / 2;
                target[outIndex++] = (short) (mono0 + frac * (mono1 - mono0));
            } else if (srcCh == 1 && tgtCh == 2) {
                // Mono to Stereo upmix (duplicate to L & R)
                short s0 = src[frame0];
                short s1 = src[frame1];
                short val = (short) (s0 + frac * (s1 - s0));
                target[outIndex++] = val;
                target[outIndex++] = val;
            } else {
                // Stereo to Stereo (or multichannel direct mapped)
                int minCh = Math.min(srcCh, tgtCh);
                for (int c = 0; c < minCh; c++) {
                    short s0 = src[frame0 * srcCh + c];
                    short s1 = src[frame1 * srcCh + c];
                    target[outIndex++] = (short) (s0 + frac * (s1 - s0));
                }
                for (int c = minCh; c < tgtCh; c++) {
                    target[outIndex++] = 0;
                }
            }

            currentFrame += frameStep;
            if (currentFrame >= totalSrcFrames) {
                currentFrame -= totalSrcFrames;
            }
        }

        playbackFrameIndex = currentFrame;
    }

    public static void readShorts(short[] target, int offset, int size) {
        readShorts(target, offset, size, 16000, 1);
    }

    /**
     * Reads PCM bytes into ByteBuffer with dynamic resampling.
     */
    public static synchronized void readByteBuffer(ByteBuffer target, int size, int targetSampleRate, int targetChannels) {
        if (target == null || size <= 0) {
            return;
        }

        byte[] temp = new byte[size];
        readBytes(temp, 0, size, targetSampleRate, targetChannels);
        target.put(temp);
    }
}
