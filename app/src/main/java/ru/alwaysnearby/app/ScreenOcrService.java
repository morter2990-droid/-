package ru.alwaysnearby.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenOcrService extends Service {
    static final String ACTION_START = "ru.alwaysnearby.app.OCR_START";
    static final String ACTION_STOP = "ru.alwaysnearby.app.OCR_STOP";
    static final String EXTRA_RESULT_CODE = "resultCode";
    static final String EXTRA_DATA = "data";
    private static final String CHANNEL = "screen_ocr";
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread thread;
    private Handler handler;
    private TextRecognizer recognizer;
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private long lastFrameAt = 0;

    @Override public void onCreate() {
        super.onCreate();
        thread = new HandlerThread("screen-ocr");
        thread.start();
        handler = new Handler(thread.getLooper());
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        if (ACTION_STOP.equals(intent.getAction())) { stopCapture(); stopSelf(); return START_NOT_STICKY; }
        if (!ACTION_START.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }

        startForeground(71, notification());
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent data;
        if (Build.VERSION.SDK_INT >= 33) data = intent.getParcelableExtra(EXTRA_DATA, Intent.class);
        else data = intent.getParcelableExtra(EXTRA_DATA);
        if (data == null) { stopSelf(); return START_NOT_STICKY; }

        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        projection = manager.getMediaProjection(resultCode, data);
        if (projection == null) { stopSelf(); return START_NOT_STICKY; }
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { stopCapture(); stopSelf(); }
        }, handler);

        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int density = metrics.densityDpi;
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
        imageReader.setOnImageAvailableListener(reader -> processFrame(reader), handler);
        virtualDisplay = projection.createVirtualDisplay("AlwaysNearbyOCR", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, handler);
        return START_NOT_STICKY;
    }

    private void processFrame(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null || processing.get()) return;
            long now = SystemClock.elapsedRealtime();
            if (now - lastFrameAt < 1800) return;
            lastFrameAt = now;
            processing.set(true);

            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int paddedWidth = rowStride / pixelStride;
            Bitmap padded = Bitmap.createBitmap(paddedWidth, image.getHeight(), Bitmap.Config.ARGB_8888);
            buffer.rewind();
            padded.copyPixelsFromBuffer(buffer);
            Bitmap bitmap = Bitmap.createBitmap(padded, 0, 0, image.getWidth(), image.getHeight());
            padded.recycle();

            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener(result -> {
                    String text = result.getText();
                    if (text != null && !text.trim().isEmpty()) {
                        Intent broadcast = new Intent(TwoGisAccessibilityService.ACTION_VISIBLE_TEXT);
                        broadcast.setPackage(getPackageName());
                        broadcast.putExtra(TwoGisAccessibilityService.EXTRA_TEXT, "РАСПОЗНАННЫЙ ТЕКСТ ЭКРАНА:\n" + text);
                        sendBroadcast(broadcast);
                    }
                })
                .addOnCompleteListener(task -> {
                    bitmap.recycle();
                    processing.set(false);
                });
        } catch (Exception ignored) {
            processing.set(false);
        } finally {
            if (image != null) image.close();
        }
    }

    private Notification notification() {
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .setContentTitle("Всегда рядом")
                    .setContentText("Захват экрана активен. Открой 2ГИС, затем вернись и останови захват.")
                    .setOngoing(true)
                    .build();
        }
        return new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Всегда рядом")
                .setContentText("Захват экрана активен.")
                .setOngoing(true)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Распознавание экрана", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    private void stopCapture() {
        try { if (virtualDisplay != null) virtualDisplay.release(); } catch (Exception ignored) {}
        virtualDisplay = null;
        try { if (imageReader != null) imageReader.close(); } catch (Exception ignored) {}
        imageReader = null;
        try { if (projection != null) projection.stop(); } catch (Exception ignored) {}
        projection = null;
    }

    @Override public void onDestroy() {
        stopCapture();
        if (recognizer != null) recognizer.close();
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
