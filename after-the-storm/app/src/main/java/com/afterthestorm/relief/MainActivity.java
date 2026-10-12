package com.afterthestorm.relief;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.JavascriptInterface;
import android.util.Base64;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

/** Fully offline native Android shell for the procedural cleanup simulation. */
public final class MainActivity extends Activity {
    private WebView web;
    public final class AssetBridge {
        @JavascriptInterface public String readSkidder() {
            try (InputStream in = getAssets().open("skidder.glb")) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] chunk = new byte[32768];
                int n;
                while ((n = in.read(chunk)) != -1) out.write(chunk, 0, n);
                return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
            } catch (Exception e) { return ""; }
        }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        web = new WebView(this);
        web.setBackgroundColor(0xff09131b);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setWebChromeClient(new WebChromeClient());
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setDefaultTextEncodingName("UTF-8");
        web.addJavascriptInterface(new AssetBridge(), "AndroidAssets");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }
    @Override protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}