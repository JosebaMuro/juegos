package com.joseba.juegos.libres;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Juega a un juego HTML5 incluido en assets/web/&lt;id&gt;/. Se sirve desde un dominio interno
 * (sin Internet) y cualquier petición externa (anuncios, analítica…) se bloquea.
 */
public class WebJuegoActivity extends Activity {
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_INICIO = "inicio";
    public static final String EXTRA_ANCHO = "ancho";
    private static final String HOST = "https://appassets.androidplatform.net/";

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        String id = getIntent().getStringExtra(EXTRA_ID);
        String inicio = getIntent().getStringExtra(EXTRA_INICIO);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);      // récords y partidas guardadas (localStorage)
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        if (getIntent().getBooleanExtra(EXTRA_ANCHO, false)) {
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(true);
            s.setBuiltInZoomControls(true);
            s.setDisplayZoomControls(false);
        }
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                return serve(req.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return serve(Uri.parse(url));
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Los enlaces externos (redes sociales, webs de los autores) no se abren dentro del juego.
                return !url.startsWith(HOST);
            }
        });
        setContentView(web);
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(HOST + "web/" + id + "/" + inicio);
    }

    private WebResourceResponse serve(Uri uri) {
        if (uri.toString().startsWith(HOST)) {
            String path = uri.getPath();
            if (path != null && path.startsWith("/web/") && !path.contains("..")) {
                try {
                    InputStream in = getAssets().open(Uri.decode(path.substring(1)));
                    return new WebResourceResponse(mime(path), mime(path).startsWith("text") ? "UTF-8" : null, in);
                } catch (Exception ignored) {
                }
            }
        }
        // Nada sale a Internet.
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not found", null,
                new ByteArrayInputStream(new byte[0]));
    }

    private static String mime(String p) {
        String l = p.toLowerCase(java.util.Locale.ROOT);
        if (l.endsWith(".html") || l.endsWith(".htm")) return "text/html";
        if (l.endsWith(".js")) return "application/javascript";
        if (l.endsWith(".css")) return "text/css";
        if (l.endsWith(".json")) return "application/json";
        if (l.endsWith(".svg")) return "image/svg+xml";
        if (l.endsWith(".png")) return "image/png";
        if (l.endsWith(".jpg") || l.endsWith(".jpeg")) return "image/jpeg";
        if (l.endsWith(".gif")) return "image/gif";
        if (l.endsWith(".ico")) return "image/x-icon";
        if (l.endsWith(".mp3")) return "audio/mpeg";
        if (l.endsWith(".ogg")) return "audio/ogg";
        if (l.endsWith(".wav")) return "audio/wav";
        if (l.endsWith(".flac")) return "audio/flac";
        if (l.endsWith(".woff")) return "font/woff";
        if (l.endsWith(".ttf")) return "font/ttf";
        if (l.endsWith(".otf")) return "font/otf";
        if (l.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
