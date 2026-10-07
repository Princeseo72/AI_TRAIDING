package com.kplay.horseracing.gumvit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    static final class JSONObjectQuote { static String quote(String s){return org.json.JSONObject.quote(s);} }
    private WebView webView;
    private HistBridge histBridge;

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
        histBridge = new HistBridge(this);
        webView.addJavascriptInterface(histBridge, "AndroidHist");
        webView.addJavascriptInterface(new GumvitBridge(), "AndroidRace");
        webView.addJavascriptInterface(new AppControlBridge(), "AndroidApp");
        webView.loadUrl("file:///android_asset/index.html");
    }

    public void dispatchHistEvent(String name,String json){if(webView!=null)webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('"+name+"',{detail:"+JSONObjectQuote.quote(json)+"}));",null);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==HistBridge.PICK_HIST&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));}catch(Exception ignored){}new Thread(()->histBridge.onPicked(u)).start();}}
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
