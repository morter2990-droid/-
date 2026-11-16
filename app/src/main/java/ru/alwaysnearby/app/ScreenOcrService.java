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

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
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
    private TessBaseAPI tess;
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private volatile boolean modelReady = false;
    private long lastFrameAt = 0;

    @Override public void onCreate() {
        super.onCreate();
        thread = new HandlerThread("screen-ocr");
        thread.start();
        handler = new Handler(thread.getLooper());
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

        new Thread(this::prepareRussianEnglishOcr, "ocr-model-setup").start();
        sendText("Захват включён. Подготавливаются встроенные языки русского и английского.");
        return START_NOT_STICKY;
    }

    private void prepareRussianEnglishOcr() {
        try {
            File base = new File(getFilesDir(), "tesseract");
            File tessdata = new File(base, "tessdata");
            if (!tessdata.exists() && !tessdata.mkdirs()) {
                throw new Exception("Не удалось создать папку языковых моделей");
            }
            copyBundledModel(tessdata, "rus");
            copyBundledModel(tessdata, "eng");

            TessBaseAPI api = new TessBaseAPI();
            if (!api.init(base.getAbsolutePath(), "rus+eng")) {
                api.end();
                throw new Exception("Tesseract не смог инициализировать rus+eng. Проверьте файлы моделей в APK.");
            }
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
            tess = api;
            modelReady = true;
            sendText("ГОТОВО: встроенные языки русского и English включены. Интернет для OCR не нужен. Открой 2ГИС.");
        } catch (Exception e) {
            String reason = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
            sendText("ОШИБКА ПОДГОТОВКИ OCR: " + reason);
        }
    }

    private void copyBundledModel(File directory, String language) throws Exception {
        File target = new File(directory, language + ".traineddata");
        if (target.exists() && target.length() > 1000000) return;
        File temporary = new File(directory, language + ".traineddata.tmp");
        try (InputStream in = getAssets().open("tessdata/" + language + ".traineddata");
             FileOutputStream out = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        }
        if (temporary.length() < 1000000) {
            temporary.delete();
            throw new Exception("Встроенная модель " + language + " отсутствует или повреждена");
        }
        if (target.exists()) target.delete();
        if (!temporary.renameTo(target)) {
            temporary.delete();
            throw new Exception("Не удалось скопировать модель " + language);
        }
    }

    private void processFrame(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null || processing.get() || !modelReady || tess == null) return;
            long now = SystemClock.elapsedRealtime();
            if (now - lastFrameAt < 2500) return;
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

            try {
                tess.setImage(bitmap);
                String recognized = tess.getUTF8Text();
                if (recognized != null && !recognized.trim().isEmpty()) {
                    sendText("РАСПОЗНАННЫЙ ТЕКСТ (РУССКИЙ + ENGLISH):\n" + recognized.trim());
                }
            } finally {
                bitmap.recycle();
                processing.set(false);
            }
        } catch (Exception e) {
            processing.set(false);
            sendText("ОШИБКА OCR: " + e.getMessage());
        } finally {
            if (image != null) image.close();
        }
    }

    private void sendText(String text) {
        Intent broadcast = new Intent(TwoGisAccessibilityService.ACTION_VISIBLE_TEXT);
        broadcast.setPackage(getPackageName());
        broadcast.putExtra(TwoGisAccessibilityService.EXTRA_TEXT, text);
        sendBroadcast(broadcast);
    }

    private Notification notification() {
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .setContentTitle("Всегда рядом")
                    .setContentText("OCR: русский + English")
                    .setOngoing(true).build();
        }
        return new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Всегда рядом")
                .setContentText("Распознавание экрана активно.")
                .setOngoing(true).build();
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
        modelReady = false;
        if (tess != null) { tess.end(); tess = null; }
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
