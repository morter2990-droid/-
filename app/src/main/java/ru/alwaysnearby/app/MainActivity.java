package ru.alwaysnearby.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int LOCATION_REQUEST = 42;
    private TextView status, coordinates;
    private LocationManager locationManager;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(5, 12, 10));
        getWindow().setNavigationBarColor(Color.rgb(5, 12, 10));
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(34), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(5, 12, 10));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView logo = new TextView(this);
        logo.setText("◉  ВСЕГДА РЯДОМ");
        logo.setTextColor(Color.rgb(57, 255, 145));
        logo.setTextSize(25);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setGravity(Gravity.CENTER);
        root.addView(logo, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("PERSONAL LOCATION SYSTEM  •  v0.1");
        subtitle.setTextColor(Color.rgb(105, 145, 122));
        subtitle.setTextSize(11);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = matchWrap();
        sp.topMargin = dp(10);
        root.addView(subtitle, sp);

        TextView panelTitle = new TextView(this);
        panelTitle.setText("МОЁ МЕСТОПОЛОЖЕНИЕ");
        panelTitle.setTextColor(Color.rgb(57, 255, 145));
        panelTitle.setTextSize(13);
        panelTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tp = matchWrap();
        tp.topMargin = dp(58);
        root.addView(panelTitle, tp);

        status = new TextView(this);
        status.setText("●  ОЖИДАНИЕ РАЗРЕШЕНИЯ");
        status.setTextColor(Color.rgb(255, 190, 70));
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stp = matchWrap();
        stp.topMargin = dp(22);
        root.addView(status, stp);

        coordinates = new TextView(this);
        coordinates.setText("Координаты пока не получены");
        coordinates.setTextColor(Color.WHITE);
        coordinates.setTextSize(16);
        coordinates.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cp = matchWrap();
        cp.topMargin = dp(18);
        root.addView(coordinates, cp);

        Button locate = new Button(this);
        locate.setText("ОПРЕДЕЛИТЬ МОЁ МЕСТОПОЛОЖЕНИЕ");
        locate.setTextColor(Color.BLACK);
        locate.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(57, 255, 145)));
        LinearLayout.LayoutParams bp = matchWrap();
        bp.topMargin = dp(36);
        root.addView(locate, bp);
        locate.setOnClickListener(v -> requestLocation());

        TextView note = new TextView(this);
        note.setText("СЕЙЧАС: локальное определение координат телефона.\nОбмен геопозицией с друзьями и серверная синхронизация ещё не подключены.");
        note.setTextColor(Color.rgb(130, 150, 138));
        note.setTextSize(12);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = matchWrap();
        np.topMargin = dp(42);
        root.addView(note, np);

        setContentView(root);
        if (hasLocationPermission()) showLocation();
        else status.setText("●  НАЖМИТЕ КНОПКУ ДЛЯ ДОСТУПА");
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
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (last == null) last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (last != null) update(last);
            else {
                status.setText("●  ПОИСК GPS...");
                status.setTextColor(Color.rgb(255, 190, 70));
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
        status.setTextColor(Color.rgb(57, 255, 145));
        coordinates.setText(String.format(java.util.Locale.US, "Широта: %.6f\nДолгота: %.6f\nТочность: %.0f м", l.getLatitude(), l.getLongitude(), l.getAccuracy()));
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == LOCATION_REQUEST) {
            if (hasLocationPermission()) showLocation();
            else status.setText("●  РАЗРЕШЕНИЕ НЕ ПРЕДОСТАВЛЕНО");
        }
    }
}
