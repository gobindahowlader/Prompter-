package com.personal.prompter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FloatService extends Service {
    static String text = "";
    static String settings = "{}";

    private WindowManager wm;
    private LinearLayout root;
    private WebView web;
    private WindowManager.LayoutParams lp;

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        goForeground();
        if (root == null) {
            build();
        } else {
            web.reload();
        }
        return START_NOT_STICKY;
    }

    private void goForeground() {
        String ch = "prompter";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(ch, "Floating prompter", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder nb = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, ch) : new Notification.Builder(this);
        Notification n = nb.setContentTitle("Prompter is floating")
                .setContentText("Tap to open the editor")
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pi)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private TextView cell(String t, int sp) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(sp);
        tv.setTextColor(Color.WHITE);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    private void changeAlpha(float d) {
        lp.alpha = Math.min(1f, Math.max(0.3f, lp.alpha + d));
        wm.updateViewLayout(root, lp);
    }

    private void build() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = (int) (dm.widthPixels * 0.94f);
        int h = (int) (dm.heightPixels * 0.42f);
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(w, h, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = (dm.widthPixels - w) / 2;
        lp.y = dp(80);
        lp.alpha = 0.92f;

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF111111);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setBackgroundColor(0xFF1D2431);

        TextView handle = cell("Drag to move", 13);
        handle.setOnTouchListener(new Mover(false));
        top.addView(handle, new LinearLayout.LayoutParams(0, dp(44), 1f));

        TextView grip = cell("Resize", 13);
        grip.setOnTouchListener(new Mover(true));
        top.addView(grip, new LinearLayout.LayoutParams(dp(64), dp(44)));

        TextView less = cell("\u2212", 22);
        less.setOnClickListener(v -> changeAlpha(-0.1f));
        top.addView(less, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView more = cell("+", 22);
        more.setOnClickListener(v -> changeAlpha(0.1f));
        top.addView(more, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView close = cell("\u2715", 20);
        close.setOnClickListener(v -> stopSelf());
        top.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));

        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new FloatBridge(), "Android");
        web.loadUrl("file:///android_asset/index.html?float=1");
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        wm.addView(root, lp);
    }

    private class Mover implements View.OnTouchListener {
        private final boolean resize;
        private int ox, oy;
        private float tx, ty;

        Mover(boolean resize) {
            this.resize = resize;
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                ox = resize ? lp.width : lp.x;
                oy = resize ? lp.height : lp.y;
                tx = e.getRawX();
                ty = e.getRawY();
            } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                int dx = (int) (e.getRawX() - tx);
                int dy = (int) (e.getRawY() - ty);
                if (resize) {
                    lp.width = Math.max(dp(200), ox + dx);
                    lp.height = Math.max(dp(160), oy + dy);
                } else {
                    lp.x = ox + dx;
                    lp.y = oy + dy;
                }
                wm.updateViewLayout(root, lp);
            }
            return true;
        }
    }

    class FloatBridge {
        @JavascriptInterface
        public String getText() {
            return text;
        }

        @JavascriptInterface
        public String getSettings() {
            return settings;
        }

        @JavascriptInterface
        public void keepAwake(boolean on) {
        }

        @JavascriptInterface
        public void closeFloat() {
            new Handler(Looper.getMainLooper()).post(() -> stopSelf());
        }
    }

    @Override
    public void onDestroy() {
        try {
            if (root != null) {
                wm.removeView(root);
                root.removeAllViews();
                web.destroy();
                root = null;
            }
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}
