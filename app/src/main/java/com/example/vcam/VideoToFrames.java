package com.example.vcam;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import com.example.vcam.HookMain;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;

import de.robv.android.xposed.XposedBridge;

//以下代码修改自 https://github.com/zhantong/Android-VideoToImages
public class VideoToFrames implements Runnable {
    private static final String TAG = "VideoToFrames";
    private static final boolean VERBOSE = false;
    private static final long DEFAULT_TIMEOUT_US = 10000;

    private static final int COLOR_FormatI420 = 1;
    private static final int COLOR_FormatNV21 = 2;


    private final int decodeColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;

    private LinkedBlockingQueue<byte[]> mQueue;
    private OutputImageFormat outputImageFormat;
    private boolean stopDecode = false;

    private String videoFilePath;
    private Throwable throwable;
    private Thread childThread;
    private Surface play_surf;

    private Callback callback;

    public interface Callback {
        void onFinishDecode();

        void onDecodeFrame(int index);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setEnqueue(LinkedBlockingQueue<byte[]> queue) {
        mQueue = queue;
    }

    //设置输出位置，没啥用
    public void setSaveFrames(String dir, OutputImageFormat imageFormat) throws IOException {
        outputImageFormat = imageFormat;

    }

    public void set_surfcae(Surface player_surface) {
        if (player_surface != null) {
            play_surf = player_surface;
        }
    }

    public void stopDecode() {
        stopDecode = true;
    }

    public void decode(String videoFilePath) throws Throwable {
        this.videoFilePath = videoFilePath;
        if (childThread == null) {
            childThread = new Thread(this, "decode");
            childThread.start();
            if (throwable != null) {
                throw throwable;
            }
        }
    }

    public void run() {
        try {
            videoDecode(videoFilePath);
        } catch (Throwable t) {
            throwable = t;
        }
    }

    @SuppressLint("WrongConstant")
    public void videoDecode(String videoFilePath) throws IOException {
        XposedBridge.log("【VCAM】【decoder】开始解码");
        MediaExtractor extractor = null;
        MediaCodec decoder = null;
        try {
            File videoFile = new File(videoFilePath);
            extractor = new MediaExtractor();
            extractor.setDataSource(videoFilePath);
            int trackIndex = selectTrack(extractor);
            if (trackIndex < 0) {
                XposedBridge.log("【VCAM】【decoder】No video track found in " + videoFilePath);
            }
            extractor.selectTrack(trackIndex);
            MediaFormat mediaFormat = extractor.getTrackFormat(trackIndex);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);
            showSupportedColorFormat(decoder.getCodecInfo().getCapabilitiesForType(mime));
            if (isColorFormatSupported(decodeColorFormat, decoder.getCodecInfo().getCapabilitiesForType(mime))) {
                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, decodeColorFormat);
                XposedBridge.log("【VCAM】【decoder】set decode color format to type " + decodeColorFormat);
            } else {
                Log.i(TAG, "unable to set decode color format, color format type " + decodeColorFormat + " not supported");
                XposedBridge.log("【VCAM】【decoder】unable to set decode color format, color format type " + decodeColorFormat + " not supported");
            }
            decodeFramesToImage(decoder, extractor, mediaFormat);
            decoder.stop();
            while (!stopDecode) {
                extractor.seekTo(0, 0);
                decodeFramesToImage(decoder, extractor, mediaFormat);
                decoder.stop();
            }
        }catch (Exception e){
            XposedBridge.log("【VCAM】[videofile]"+ e.toString());
        } finally {
            if (decoder != null) {
                decoder.stop();
                decoder.release();
                decoder = null;
            }
            if (extractor != null) {
                extractor.release();
                extractor = null;
            }
        }
    }

    private void showSupportedColorFormat(MediaCodecInfo.CodecCapabilities caps) {
        System.out.print("supported color format: ");
        for (int c : caps.colorFormats) {
            System.out.print(c + "\t");
        }
        System.out.println();
    }

    private boolean isColorFormatSupported(int colorFormat, MediaCodecInfo.CodecCapabilities caps) {
        for (int c : caps.colorFormats) {
            if (c == colorFormat) {
                return true;
            }
        }
        return false;
    }

    private void decodeFramesToImage(MediaCodec decoder, MediaExtractor extractor, MediaFormat mediaFormat) {
        boolean is_first = false;
        long startWhen = 0;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        decoder.configure(mediaFormat, play_surf, null, 0);
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        decoder.start();
        final int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        final int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
        int outputFrameCount = 0;
        while (!sawOutputEOS && !stopDecode) {
            if (!sawInputEOS) {
                int inputBufferId = decoder.dequeueInputBuffer(DEFAULT_TIMEOUT_US);
                if (inputBufferId >= 0) {
                    ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        sawInputEOS = true;
                    } else {
                        long presentationTimeUs = extractor.getSampleTime();
                        decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0);
                        extractor.advance();
                    }
                }
            }
            int outputBufferId = decoder.dequeueOutputBuffer(info, DEFAULT_TIMEOUT_US);
            if (outputBufferId >= 0) {
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }
                boolean doRender = (info.size != 0);
                if (doRender) {
                    outputFrameCount++;
                    if (callback != null) {
                        callback.onDecodeFrame(outputFrameCount);
                    }
                    if (!is_first) {
                        startWhen = System.currentTimeMillis();
                        is_first = true;
                    }
                    if (play_surf == null) {
                        Image image = decoder.getOutputImage(outputBufferId);
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] arr = new byte[buffer.remaining()];
                        buffer.get(arr);
                        if (mQueue != null) {
                            try {
                                mQueue.put(arr);
                            } catch (InterruptedException e) {
                                XposedBridge.log("【VCAM】" + e.toString());
                            }
                        }
                        if (outputImageFormat != null) {
                            byte[] nv21 = getDataFromImage(image, COLOR_FormatNV21);
                            if (HookMain.config != null) {
                                if (HookMain.config.mirror) {
                                    nv21 = mirrorNV21(nv21, width, height);
                                }
                                if (HookMain.config.rotation > 0) {
                                    nv21 = rotateAndFitNV21(nv21, width, height, HookMain.config.rotation, HookMain.config.scaleMode);
                                }
                                if (HookMain.config.antiDetection) {
                                    AntiDetection.processFrame(nv21, width, height, true, HookMain.config.noiseIntensity);
                                }
                            }
                            HookMain.data_buffer = nv21;
                        }
                        image.close();
                    }
                    while (HookMain.config != null && HookMain.config.playbackPaused && !stopDecode) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ignored) {}
                    }
                    long sleepTime = info.presentationTimeUs / 1000 - (System.currentTimeMillis() - startWhen);
                    if (sleepTime > 0) {
                        try {
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException e) {
                            XposedBridge.log("【VCAM】" + e.toString());
                            XposedBridge.log("【VCAM】线程延迟出错");
                        }
                    }
                    decoder.releaseOutputBuffer(outputBufferId, true);
                }
            }
        }
        if (callback != null) {
            callback.onFinishDecode();
        }
    }

    private static int selectTrack(MediaExtractor extractor) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                if (VERBOSE) {
                    Log.d(TAG, "Extractor selected track " + i + " (" + mime + "): " + format);
                }
                return i;
            }
        }
        return -1;
    }

    private static boolean isImageFormatSupported(Image image) {
        int format = image.getFormat();
        switch (format) {
            case ImageFormat.YUV_420_888:
            case ImageFormat.NV21:
            case ImageFormat.YV12:
                return true;
        }
        return false;
    }

    private static byte[] getDataFromImage(Image image, int colorFormat) {
        if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) {
            throw new IllegalArgumentException("only support COLOR_FormatI420 " + "and COLOR_FormatNV21");
        }
        if (!isImageFormatSupported(image)) {
            throw new RuntimeException("can't convert Image to byte array, format " + image.getFormat());
        }
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];
        if (VERBOSE) Log.v(TAG, "get data from " + planes.length + " planes");
        int channelOffset = 0;
        int outputStride = 1;
        for (int i = 0; i < planes.length; i++) {
            switch (i) {
                case 0:
                    channelOffset = 0;
                    outputStride = 1;
                    break;
                case 1:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = width * height;
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height + 1;
                        outputStride = 2;
                    }
                    break;
                case 2:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = (int) (width * height * 1.25);
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height;
                        outputStride = 2;
                    }
                    break;
            }
            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();
            if (VERBOSE) {
                Log.v(TAG, "pixelStride " + pixelStride);
                Log.v(TAG, "rowStride " + rowStride);
                Log.v(TAG, "width " + width);
                Log.v(TAG, "height " + height);
                Log.v(TAG, "buffer size " + buffer.remaining());
            }
            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(data, channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
            if (VERBOSE) Log.v(TAG, "Finished reading data from plane " + i);
        }
        return data;
    }

    public static byte[] rotateAndFitNV21(byte[] data, int width, int height, int rotation, String scaleMode) {
        if (data == null || rotation == 0) return data;
        if (rotation == 180) {
            return rotateNV21(data, width, height, 180);
        }
        if (rotation == 90 || rotation == 270) {
            byte[] rotated = rotateNV21(data, width, height, rotation);
            // Rotated frame dimension is (height, width)
            return fitNV21(rotated, height, width, width, height, scaleMode);
        }
        return data;
    }

    public static byte[] fitNV21(byte[] src, int srcW, int srcH, int dstW, int dstH, String scaleMode) {
        if (src == null || srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return src;
        int dstFrameSize = dstW * dstH;
        byte[] dst = new byte[dstFrameSize * 3 / 2];

        // Fill with black / neutral YUV: Y = 16, VU = 128
        Arrays.fill(dst, 0, dstFrameSize, (byte) 16);
        Arrays.fill(dst, dstFrameSize, dst.length, (byte) 128);

        int scaledW, scaledH, offsetX, offsetY;
        if ("stretch".equalsIgnoreCase(scaleMode)) {
            scaledW = dstW;
            scaledH = dstH;
            offsetX = 0;
            offsetY = 0;
        } else if ("fill".equalsIgnoreCase(scaleMode)) {
            double scale = Math.max((double) dstW / srcW, (double) dstH / srcH);
            scaledW = (int) Math.round(srcW * scale) & ~1;
            scaledH = (int) Math.round(srcH * scale) & ~1;
            offsetX = ((dstW - scaledW) / 2) & ~1;
            offsetY = ((dstH - scaledH) / 2) & ~1;
        } else {
            // Default: "fit" (letterbox)
            double scale = Math.min((double) dstW / srcW, (double) dstH / srcH);
            scaledW = (int) Math.round(srcW * scale) & ~1;
            scaledH = (int) Math.round(srcH * scale) & ~1;
            offsetX = ((dstW - scaledW) / 2) & ~1;
            offsetY = ((dstH - scaledH) / 2) & ~1;
        }

        int srcFrameSize = srcW * srcH;

        // Sample Y plane
        int startY = Math.max(0, offsetY);
        int endY = Math.min(dstH, offsetY + scaledH);
        int startX = Math.max(0, offsetX);
        int endX = Math.min(dstW, offsetX + scaledW);

        for (int dy = startY; dy < endY; dy++) {
            int sy = ((dy - offsetY) * srcH) / scaledH;
            if (sy < 0) sy = 0;
            if (sy >= srcH) sy = srcH - 1;
            int srcRow = sy * srcW;
            int dstRow = dy * dstW;

            for (int dx = startX; dx < endX; dx++) {
                int sx = ((dx - offsetX) * srcW) / scaledW;
                if (sx < 0) sx = 0;
                if (sx >= srcW) sx = srcW - 1;
                dst[dstRow + dx] = src[srcRow + sx];
            }
        }

        // Sample VU plane (2x2 subsampled)
        int uvStartY = startY / 2;
        int uvEndY = (endY + 1) / 2;
        int uvStartX = startX / 2;
        int uvEndX = (endX + 1) / 2;
        int dstUvHeight = dstH / 2;
        int srcUvHeight = srcH / 2;

        for (int duy = uvStartY; duy < uvEndY && duy < dstUvHeight; duy++) {
            int suy = ((duy * 2 - offsetY) * srcH) / (scaledH * 2);
            if (suy < 0) suy = 0;
            if (suy >= srcUvHeight) suy = srcUvHeight - 1;

            int srcUvRow = srcFrameSize + suy * srcW;
            int dstUvRow = dstFrameSize + duy * dstW;

            for (int dux = uvStartX; dux < uvEndX && dux < (dstW / 2); dux++) {
                int sux = ((dux * 2 - offsetX) * srcW) / (scaledW * 2);
                if (sux < 0) sux = 0;
                if (sux >= (srcW / 2)) sux = (srcW / 2) - 1;

                int srcIndex = srcUvRow + sux * 2;
                int dstIndex = dstUvRow + dux * 2;
                if (srcIndex + 1 < src.length && dstIndex + 1 < dst.length) {
                    dst[dstIndex] = src[srcIndex];         // V
                    dst[dstIndex + 1] = src[srcIndex + 1]; // U
                }
            }
        }

        return dst;
    }

    public static byte[] rotateNV21(byte[] data, int width, int height, int rotation) {
        if (rotation == 0 || data == null) return data;
        int frameSize = width * height;
        byte[] rotated = new byte[data.length];

        if (rotation == 90) {
            int k = 0;
            for (int i = 0; i < width; i++) {
                for (int j = height - 1; j >= 0; j--) {
                    rotated[k++] = data[j * width + i];
                }
            }
            int uvHeight = height / 2;
            int uvStartIndex = frameSize;
            int n = frameSize;
            for (int i = 0; i < width; i += 2) {
                for (int j = uvHeight - 1; j >= 0; j--) {
                    rotated[n++] = data[uvStartIndex + j * width + i];
                    rotated[n++] = data[uvStartIndex + j * width + i + 1];
                }
            }
            return rotated;
        } else if (rotation == 180) {
            int k = 0;
            for (int i = frameSize - 1; i >= 0; i--) {
                rotated[k++] = data[i];
            }
            int n = frameSize;
            for (int i = data.length - 2; i >= frameSize; i -= 2) {
                rotated[n++] = data[i];
                rotated[n++] = data[i + 1];
            }
            return rotated;
        } else if (rotation == 270) {
            int k = 0;
            for (int i = width - 1; i >= 0; i--) {
                for (int j = 0; j < height; j++) {
                    rotated[k++] = data[j * width + i];
                }
            }
            int uvHeight = height / 2;
            int uvStartIndex = frameSize;
            int n = frameSize;
            for (int i = width - 2; i >= 0; i -= 2) {
                for (int j = 0; j < uvHeight; j++) {
                    rotated[n++] = data[uvStartIndex + j * width + i];
                    rotated[n++] = data[uvStartIndex + j * width + i + 1];
                }
            }
            return rotated;
        }
        return data;
    }

    public static byte[] mirrorNV21(byte[] data, int width, int height) {
        if (data == null) return null;
        byte[] mirrored = new byte[data.length];
        // Mirror Y plane
        for (int y = 0; y < height; y++) {
            int rowStart = y * width;
            for (int x = 0; x < width; x++) {
                mirrored[rowStart + x] = data[rowStart + (width - 1 - x)];
            }
        }
        // Mirror VU plane (interleaved pairs)
        int frameSize = width * height;
        int uvHeight = height / 2;
        for (int y = 0; y < uvHeight; y++) {
            int rowStart = frameSize + y * width;
            for (int x = 0; x < width; x += 2) {
                int targetX = width - 2 - x;
                mirrored[rowStart + x] = data[rowStart + targetX];
                mirrored[rowStart + x + 1] = data[rowStart + targetX + 1];
            }
        }
        return mirrored;
    }

}

enum OutputImageFormat {
    I420("I420"),
    NV21("NV21"),
    JPEG("JPEG");
    private final String friendlyName;

    OutputImageFormat(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    public String toString() {
        return friendlyName;
    }
}



