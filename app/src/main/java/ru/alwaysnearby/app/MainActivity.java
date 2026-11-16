package ru.alwaysnearby.app;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int LOCATION_REQUEST = 42;
    private TextView status, coordinates, twoGisStatus, visibleInfo;
    private LocationManager locationManager;
    private BroadcastReceiver twoGisReceiver;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(5, 12, 10));
        getWindow().setNavigationBarColor(Color.rgb(5, 12, 10));
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(28));
        root.setBackgroundColor(Color.rgb(5, 12, 10));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root);

        TextView logo = new TextView(this);
        logo.setText("◉  ВСЕГДА РЯДОМ");
        logo.setTextColor(Color.rgb(57, 255, 145));
        logo.setTextSize(25);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setGravity(Gravity.CENTER);
        root.addView(logo, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("PERSONAL LOCATION SYSTEM  •  v0.2");
        subtitle.setTextColor(Color.rgb(105, 145, 122));
        subtitle.setTextSize(11);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = matchWrap();
        sp.topMargin = dp(10);
        root.addView(subtitle, sp);

        addTitle(root, "МОЁ МЕСТОПОЛОЖЕНИЕ", 42);
        status = addText(root, "●  ОЖИДАНИЕ РАЗРЕШЕНИЯ", 14, Color.rgb(255,190,70), 20);
        coordinates = addText(root, "Координаты пока не получены", 16, Color.WHITE, 16);
        Button locate = makeButton("ОПРЕДЕЛИТЬ МОЁ МЕСТОПОЛОЖЕНИЕ");
        root.addView(locate, buttonParams(24));
        locate.setOnClickListener(v -> requestLocation());

        addTitle(root, "ПОДКЛЮЧЕНИЕ К 2ГИС", 38);
        twoGisStatus = addText(root, "●  ПРОВЕРЬ РАЗРЕШЕНИЕ СПЕЦИАЛЬНЫХ ВОЗМОЖНОСТЕЙ", 13, Color.rgb(255,190,70), 16);
        Button accessibility = makeButton("ВКЛЮЧИТЬ ЧТЕНИЕ ВИДИМОГО ЭКРАНА");
        root.addView(accessibility, buttonParams(14));
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        Button open2gis = makeButton("ОТКРЫТЬ 2ГИС");
        root.addView(open2gis, buttonParams(12));
        open2gis.setOnClickListener(v -> openTwoGis());

        TextView infoTitle = addText(root, "ИНФОРМАЦИЯ, КОТОРУЮ ВИДИТ ANDROID", 12, Color.rgb(57,255,145), 24);
        infoTitle.setTypeface(Typeface.DEFAULT_BOLD);
        visibleInfo = addText(root, "Пока нет данных. Включи разрешение выше, затем открой 2ГИС и перейди к разделу «Друзья на карте».", 13, Color.WHITE, 10);

        TextView note = addText(root, "Читается только видимый текст, когда 2ГИС открыт тобой. Информация обрабатывается локально на телефоне. Если координаты не отображаются как текст, приложение не сможет получить их этим способом.", 11, Color.rgb(130,150,138), 28);
        note.setGravity(Gravity.CENTER);

        setContentView(scroll);
        registerTwoGisReceiver();
        refreshAccessibilityStatus();
        if (hasLocationPermission()) showLocation();
        else status.setText("●  НАЖМИТЕ КНОПКУ ДЛЯ ДОСТУПА");
    }

    private void registerTwoGisReceiver() {
        twoGisReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String data = intent.getStringExtra(TwoGisAccessibilityService.EXTRA_TEXT);
                if (data != null && !data.trim().isEmpty()) {
                    visibleInfo.setText(data);
                    twoGisStatus.setText("●  ПОЛУЧЕН ВИДИМЫЙ ТЕКСТ ИЗ 2ГИС");
                    twoGisStatus.setTextColor(Color.rgb(57,255,145));
                } else {
                    visibleInfo.setText("2ГИС открыт, но доступный текст на экране не найден.");
                }
            }
        };
        IntentFilter filter = new IntentFilter(TwoGisAccessibilityService.ACTION_VISIBLE_TEXT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(twoGisReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(twoGisReceiver, filter);
    }

    private void refreshAccessibilityStatus() {
        boolean enabled = false;
        try {
            String services = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            enabled = services != null && services.toLowerCase().contains(getPackageName().toLowerCase());
        } catch (Exception ignored) { }
        twoGisStatus.setText(enabled ? "●  СПЕЦИАЛЬНОЕ РАЗРЕШЕНИЕ ВКЛЮЧЕНО" : "●  РАЗРЕШЕНИЕ ЕЩЁ НЕ ВКЛЮЧЕНО");
        twoGisStatus.setTextColor(enabled ? Color.rgb(57,255,145) : Color.rgb(255,190,70));
    }

    private void openTwoGis() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("ru.dublgis.dgismobile");
        if (launch != null) startActivity(launch);
        else {
            twoGisStatus.setText("●  2ГИС НЕ НАЙДЕН. УСТАНОВИ ЕГО И ПОВТОРИ.");
            twoGisStatus.setTextColor(Color.rgb(255,190,70));
        }
    }

    private void requestLocation() {
        if (!hasLocationPermission()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
        } else showLocation();
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void showLocation() {
        try {
            Location last = null;
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last == null) last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (last != null) update(last);
            else {
                status.setText("●  ПОИСК GPS...");
                locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, new LocationListener() {
                    @Override public void onLocationChanged(Location location) { update(location); }
                    @Override public void onProviderDisabled(String provider) {}
                    @Override public void onProviderEnabled(String provider) {}
                    @Override public void onStatusChanged(String provider, int s, Bundle extras) {}
                }, getMainLooper());
            }
        } catch (SecurityException e) {
            status.setText("●  НЕТ ДОСТУПА К ГЕОЛОКАЦИИ");
        } catch (Exception e) {
            status.setText("●  НЕ УДАЛОСЬ ПОЛУЧИТЬ КООРДИНАТЫ");
        }
    }

    private void update(Location l) {
        status.setText("●  МЕСТОПОЛОЖЕНИЕ ПОЛУЧЕНО");
        status.setTextColor(Color.rgb(57,255,145));
        coordinates.setText(String.format(java.util.Locale.US, "Широта: %.6f\nДолгота: %.6f\nТочность: %.0f м", l.getLatitude(), l.getLongitude(), l.getAccuracy()));
    }

    private void addTitle(LinearLayout root, String value, int top) {
        TextView t = addText(root, value, 13, Color.rgb(57,255,145), top);
        t.setTypeface(Typeface.DEFAULT_BOLD);
    }
    private TextView addText(LinearLayout root, String value, int size, int color, int top) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextColor(color); t.setTextSize(size);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p = matchWrap(); p.topMargin = dp(top);
        root.addView(t, p); return t;
    }
    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text); b.setTextColor(Color.BLACK);
        b.setTextSize(12);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(57,255,145)));
        return b;
    }
    private LinearLayout.LayoutParams buttonParams(int top) {
        LinearLayout.LayoutParams p = matchWrap(); p.topMargin = dp(top); return p;
    }
    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onResume() {
        super.onResume();
        if (twoGisStatus != null) refreshAccessibilityStatus();
    }
    @Override protected void onDestroy() {
        if (twoGisReceiver != null) unregisterReceiver(twoGisReceiver);
        super.onDestroy();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == LOCATION_REQUEST) {
            if (hasLocationPermission()) showLocation();
            else status.setText("●  РАЗРЕШЕНИЕ НЕ ПРЕДОСТАВЛЕНО");
        }
    }
}