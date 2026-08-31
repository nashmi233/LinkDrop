package com.nashmi.locationlite;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private EditText latInput;
    private EditText lonInput;
    private SharedPreferences prefs;
    private WebView map;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        prefs = getSharedPreferences("location_lite", MODE_PRIVATE);

        double savedLat = Double.longBitsToDouble(prefs.getLong("lat", Double.doubleToLongBits(24.7136)));
        double savedLon = Double.longBitsToDouble(prefs.getLong("lon", Double.doubleToLongBits(46.6753)));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.setBackgroundColor(Color.rgb(248, 248, 248));

        TextView title = new TextView(this);
        title.setText("Location Lite");
        title.setTextSize(23);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(4), 0, dp(4));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView hint = new TextView(this);
        hint.setText("بدون حساب • اضغط على الخريطة ثم تشغيل\nمرة واحدة فقط: خيارات المطور ← تطبيق الموقع الوهمي ← Location Lite");
        hint.setTextSize(13);
        hint.setTextColor(Color.DKGRAY);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, 0, 0, dp(8));
        hint.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            } catch (Exception ignored) { }
        });
        root.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout coords = new LinearLayout(this);
        coords.setOrientation(LinearLayout.HORIZONTAL);

        latInput = new EditText(this);
        latInput.setSingleLine(true);
        latInput.setText(String.format(Locale.US, "%.6f", savedLat));
        latInput.setHint("Latitude");
        latInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        coords.addView(latInput, new LinearLayout.LayoutParams(0, dp(50), 1f));

        lonInput = new EditText(this);
        lonInput.setSingleLine(true);
        lonInput.setText(String.format(Locale.US, "%.6f", savedLon));
        lonInput.setHint("Longitude");
        lonInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        LinearLayout.LayoutParams lonLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        lonLp.setMarginStart(dp(8));
        coords.addView(lonInput, lonLp);
        root.addView(coords, new LinearLayout.LayoutParams(-1, -2));

        map = new WebView(this);
        WebSettings ws = map.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        map.setBackgroundColor(Color.WHITE);
        map.addJavascriptInterface(new MapBridge(), "Android");
        loadMap(savedLat, savedLon);
        LinearLayout.LayoutParams mapLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        mapLp.topMargin = dp(8);
        mapLp.bottomMargin = dp(10);
        root.addView(map, mapLp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);

        Button start = makeButton("تشغيل");
        Button stop = makeButton("إيقاف");
        Button joystick = makeButton("Joystick");

        start.setOnClickListener(v -> startMock());
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, MockLocationService.class));
            Toast.makeText(this, "تم إيقاف الموقع الوهمي", Toast.LENGTH_SHORT).show();
        });
        joystick.setOnClickListener(v -> toggleJoystick());

        buttons.addView(start, buttonParams());
        buttons.addView(stop, buttonParams());
        buttons.addView(joystick, buttonParams());
        root.addView(buttons, new LinearLayout.LayoutParams(-1, dp(54)));

        setContentView(root);

        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        }
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(15);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        return lp;
    }

    private void startMock() {
        double[] p = readPoint();
        if (p == null) return;
        savePoint(p[0], p[1]);
        Intent i = new Intent(this, MockLocationService.class)
                .setAction(MockLocationService.ACTION_START)
                .putExtra("lat", p[0])
                .putExtra("lon", p[1]);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        Toast.makeText(this, "تم تشغيل الموقع", Toast.LENGTH_SHORT).show();
    }

    private void toggleJoystick() {
        if (!Settings.canDrawOverlays(this)) {
            Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(permission);
            Toast.makeText(this, "فعّل السماح بالظهور فوق التطبيقات ثم اضغط Joystick مرة ثانية", Toast.LENGTH_LONG).show();
            return;
        }
        double[] p = readPoint();
        if (p == null) return;
        savePoint(p[0], p[1]);
        Intent i = new Intent(this, MockLocationService.class)
                .setAction(MockLocationService.ACTION_TOGGLE_JOYSTICK)
                .putExtra("lat", p[0])
                .putExtra("lon", p[1]);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    private double[] readPoint() {
        try {
            double lat = Double.parseDouble(latInput.getText().toString().trim().replace(',', '.'));
            double lon = Double.parseDouble(lonInput.getText().toString().trim().replace(',', '.'));
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) throw new IllegalArgumentException();
            return new double[]{lat, lon};
        } catch (Exception e) {
            Toast.makeText(this, "الإحداثيات غير صحيحة", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private void savePoint(double lat, double lon) {
        prefs.edit()
                .putLong("lat", Double.doubleToRawLongBits(lat))
                .putLong("lon", Double.doubleToRawLongBits(lon))
                .apply();
    }

    private void loadMap(double lat, double lon) {
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=no'>" +
                "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>" +
                "<style>html,body,#m{height:100%;margin:0} .leaflet-control-attribution{font-size:9px}</style></head><body><div id='m'></div>" +
                "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script><script>" +
                "var lat=" + String.format(Locale.US, "%.7f", lat) + ",lon=" + String.format(Locale.US, "%.7f", lon) + ";" +
                "var map=L.map('m',{zoomControl:true}).setView([lat,lon],15);" +
                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);" +
                "var marker=L.marker([lat,lon]).addTo(map);" +
                "function setP(a,b){marker.setLatLng([a,b]);Android.setLocation(a,b);}" +
                "map.on('click',function(e){setP(e.latlng.lat,e.latlng.lng);});" +
                "</script></body></html>";
        map.loadDataWithBaseURL("https://openstreetmap.org/", html, "text/html", "UTF-8", null);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public class MapBridge {
        @JavascriptInterface
        public void setLocation(double lat, double lon) {
            runOnUiThread(() -> {
                latInput.setText(String.format(Locale.US, "%.6f", lat));
                lonInput.setText(String.format(Locale.US, "%.6f", lon));
                savePoint(lat, lon);
            });
        }
    }

    @Override
    protected void onDestroy() {
        if (map != null) {
            map.removeJavascriptInterface("Android");
            map.destroy();
        }
        super.onDestroy();
    }
}
