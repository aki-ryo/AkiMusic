package com.akiryo.akimusic;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * AkiMusic · 潮汐 TIDE
 * 一个 WebView 壳：把打包在 APK 里的前端跑起来，接口由本地 AssetServer 代理。
 */
public class MainActivity extends Activity {

    private static final String BG = "#141018";

    private WebView web;
    private AssetServer server;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.parseColor(BG));
        getWindow().setNavigationBarColor(Color.parseColor(BG));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        int port = 0;
        try {
            server = new AssetServer(getAssets());
            port = server.start();
        } catch (Exception ignored) {
        }

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor(BG));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setHorizontalScrollBarEnabled(false);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadsImagesAutomatically(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setUseWideViewPort(false);
        s.setLoadWithOverviewMode(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " AkiMusic/0.1.0");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("http://127.0.0.1")) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {
                }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient());

        setContentView(web);

        if (port > 0) {
            web.loadUrl("http://127.0.0.1:" + port + "/");
        } else {
            web.loadData(
                    "<body style='background:#141018;color:#eee;font-family:sans-serif;padding:40px'>"
                            + "<h2>本地服务启动失败</h2><p>请重启应用后再试。</p></body>",
                    "text/html; charset=utf-8",
                    "utf-8");
        }
    }

    @Override
    public void onBackPressed() {
        if (web == null) {
            super.onBackPressed();
            return;
        }
        // 先关弹层，再退页面，最后才退出应用
        final String js = "(function(){" +
                "var np=document.getElementById('np');" +
                "if(np&&np.classList.contains('is-open')){document.getElementById('npClose').click();return 'overlay';}" +
                "var q=document.getElementById('queueSheet');" +
                "if(q&&q.classList.contains('is-open')){document.getElementById('qsClose').click();return 'overlay';}" +
                "var d=document.querySelector('.dlg-scrim');" +
                "if(d){d.remove();return 'overlay';}" +
                "var sb=document.getElementById('sidebar');" +
                "if(sb&&sb.classList.contains('is-open')){sb.classList.remove('is-open');return 'overlay';}" +
                "if(location.hash&&location.hash!=='#/home'){history.back();return 'nav';}" +
                "return 'root';" +
                "})()";
        web.evaluateJavascript(js, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if (value == null || value.contains("root")) {
                    finish();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (server != null) server.stop();
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
