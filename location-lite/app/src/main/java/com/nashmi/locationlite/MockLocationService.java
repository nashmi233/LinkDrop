package com.nashmi.locationlite;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MockLocationService extends Service {
    public static final String ACTION_START = "locationlite.START";
    public static final String ACTION_TOGGLE_JOYSTICK = "locationlite.TOGGLE_JOYSTICK";

    private static final String CHANNEL = "location_lite_active";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationManager locationManager;
    private SharedPreferences prefs;
    private double lat;
    private double lon;
    private double moveEast;
    private double moveNorth;
    private boolean mockReady;
    private boolean loopStarted;
    private WindowManager windowManager;
    private View joystickView;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("location_lite", MODE_PRIVATE);
        lat = Double.longBitsToDouble(prefs.getLong("lat", Double.doubleToLongBits(24.7136)));
        lon = Double.longBitsToDouble(prefs.getLong("lon", Double.doubleToLongBits(46.6753)));
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        createNotificationChannel();
        try {
            startForeground(7, buildNotification());
        } catch (SecurityException e) {
            Toast.makeText(this, "اسمح بصلاحية الموقع أولاً", Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            ensureMocking();
            startLoop();
            return START_STICKY;
        }

        if (intent.hasExtra("lat")) lat = intent.getDoubleExtra("lat", lat);
        if (intent.hasExtra("lon")) lon = intent.getDoubleExtra("lon", lon);
        savePosition();

        String action = intent.getAction();
        if (ACTION_TOGGLE_JOYSTICK.equals(action)) {
            if (!ensureMocking()) return START_NOT_STICKY;
            startLoop();
            toggleJoystick();
        } else {
            if (!ensureMocking()) return START_NOT_STICKY;
            publishLocation();
            startLoop();
        }
        return START_STICKY;
    }

    private void createNotificationChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Location Lite", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("الموقع الوهمي يعمل");
        nm.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setContentTitle("Location Lite يعمل")
                .setContentText("اضغط للعودة إلى التطبيق")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private boolean ensureMocking() {
        if (mockReady) return true;
        try {
            addProvider(LocationManager.GPS_PROVIDER);
            addProvider(LocationManager.NETWORK_PROVIDER);
            mockReady = true;
            publishLocation();
            return true;
        } catch (SecurityException e) {
            Toast.makeText(this,
                    "اختر Location Lite كتطبيق Mock Location من خيارات المطور",
                    Toast.LENGTH_LONG).show();
            try {
                Intent settings = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(settings);
            } catch (Exception ignored) { }
            stopSelf();
            return false;
        } catch (Exception e) {
            Toast.makeText(this, "تعذر تشغيل Mock Location: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
            stopSelf();
            return false;
        }
    }

    private void addProvider(String provider) {
        try {
            locationManager.addTestProvider(provider,
                    false, false, false, false,
                    true, true, true,
                    Criteria.POWER_LOW, Criteria.ACCURACY_FINE);
        } catch (IllegalArgumentException ignored) {
            // Provider may already be registered as a test provider from a previous service restart.
        }
        locationManager.setTestProviderEnabled(provider, true);
    }

    private void publishLocation() {
        if (!mockReady) return;
        publishTo(LocationManager.GPS_PROVIDER);
        publishTo(LocationManager.NETWORK_PROVIDER);
    }

    private void publishTo(String provider) {
        try {
            Location l = new Location(provider);
            l.setLatitude(lat);
            l.setLongitude(lon);
            l.setAltitude(12.0);
            l.setAccuracy(1.0f);
            float speed = (float) (Math.hypot(moveEast, moveNorth) * 2.2);
            l.setSpeed(speed);
            if (speed > 0.01f) {
                double bearing = Math.toDegrees(Math.atan2(moveEast, moveNorth));
                if (bearing < 0) bearing += 360.0;
                l.setBearing((float) bearing);
            }
            l.setTime(System.currentTimeMillis());
            l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            locationManager.setTestProviderLocation(provider, l);
        } catch (Exception ignored) { }
    }

    private void startLoop() {
        if (loopStarted) return;
        loopStarted = true;
        handler.post(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (mockReady) {
                double magnitude = Math.hypot(moveEast, moveNorth);
                if (magnitude > 0.02) {
                    double dt = 0.25;
                    double speedMps = 2.2;
                    double eastMeters = moveEast * speedMps * dt;
                    double northMeters = moveNorth * speedMps * dt;
                    lat += northMeters / 111320.0;
                    double cosLat = Math.cos(Math.toRadians(lat));
                    if (Math.abs(cosLat) < 0.01) cosLat = 0.01;
                    lon += eastMeters / (111320.0 * cosLat);
                }
                publishLocation();
            }
            handler.postDelayed(this, 250);
        }
    };

    private void toggleJoystick() {
        if (joystickView != null) {
            removeJoystick();
            Toast.makeText(this, "تم إخفاء Joystick", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "فعّل الظهور فوق التطبيقات", Toast.LENGTH_SHORT).show();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        final int size = dp(165);
        final int radius = dp(58);

        FrameLayout pad = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.argb(150, 25, 25, 25));
        bg.setStroke(dp(2), Color.argb(210, 255, 255, 255));
        pad.setBackground(bg);

        TextView center = new TextView(this);
        center.setText("◎\nتحريك");
        center.setTextColor(Color.WHITE);
        center.setTextSize(15);
        center.setGravity(Gravity.CENTER);
        pad.addView(center, new FrameLayout.LayoutParams(-1, -1));

        pad.setOnTouchListener((v, event) -> {
            double cx = size / 2.0;
            double cy = size / 2.0;
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                moveEast = 0;
                moveNorth = 0;
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                double dx = event.getX() - cx;
                double dy = event.getY() - cy;
                double dist = Math.hypot(dx, dy);
                if (dist < dp(8)) {
                    moveEast = 0;
                    moveNorth = 0;
                } else {
                    double scale = Math.min(1.0, dist / radius);
                    moveEast = (dx / dist) * scale;
                    moveNorth = (-dy / dist) * scale;
                }
                return true;
            }
            return false;
        });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.START | Gravity.BOTTOM;
        lp.x = dp(24);
        lp.y = dp(160);
        windowManager.addView(pad, lp);
        joystickView = pad;
        Toast.makeText(this, "Joystick جاهز — اسحب للتحريك", Toast.LENGTH_SHORT).show();
    }

    private void removeJoystick() {
        moveEast = 0;
        moveNorth = 0;
        if (joystickView != null && windowManager != null) {
            try { windowManager.removeView(joystickView); } catch (Exception ignored) { }
        }
        joystickView = null;
    }

    private void savePosition() {
        prefs.edit()
                .putLong("lat", Double.doubleToRawLongBits(lat))
                .putLong("lon", Double.doubleToRawLongBits(lon))
                .apply();
    }

    private void cleanupProvider(String provider) {
        try { locationManager.removeTestProvider(provider); } catch (Exception ignored) { }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        savePosition();
        removeJoystick();
        handler.removeCallbacksAndMessages(null);
        cleanupProvider(LocationManager.GPS_PROVIDER);
        cleanupProvider(LocationManager.NETWORK_PROVIDER);
        mockReady = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
