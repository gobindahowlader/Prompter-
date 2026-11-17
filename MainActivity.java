package com.personal.prompter;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.__back&&window.__back())?'1':'0'", v -> {
            if (!"\"1\"".equals(v)) {
                moveTaskToBack(true);
            }
        });
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }

    class Bridge {
        @JavascriptInterface
        public void keepAwake(final boolean on) {
            runOnUiThread(() -> {
                if (on) {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        }

        @JavascriptInterface
        public void startFloating(final String text, final String settings) {
            runOnUiThread(() -> {
                if (!Settings.canDrawOverlays(MainActivity.this)) {
                    Intent p = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(p);
                    Toast.makeText(MainActivity.this,
                            "Allow \"Display over other apps\", then come back and tap Floating window again",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                FloatService.text = text;
                FloatService.settings = settings;
                Intent i = new Intent(MainActivity.this, FloatService.class);
                if (Build.VERSION.SDK_INT >= 26) {
                    startForegroundService(i);
                } else {
                    startService(i);
                }
                moveTaskToBack(true);
            });
        }
    }
}
