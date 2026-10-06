package com.kplay.horseracing.gumvit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView webView;

    public class AppControlBridge {
        @JavascriptInterface public void closeApp() {
            runOnUiThread(() -> {
                if (webView != null) {
                    webView.stopLoading();
                    webView.clearHistory();
                    webView.clearCache(false);
                }
                if (android.os.Build.VERSION.SDK_INT >= 21) finishAndRemoveTask(); else finishAffinity();
            });
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new StorageBridge(this), "AndroidStore");
        webView.addJavascriptInterface(new GumvitBridge(), "AndroidRace");
        webView.addJavascriptInterface(new AppControlBridge(), "AndroidApp");
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
