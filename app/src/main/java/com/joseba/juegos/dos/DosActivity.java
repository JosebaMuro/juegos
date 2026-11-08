package com.joseba.juegos.dos;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Emulador DOS: DOSBox compilado a WebAssembly (js-dos 6.22, GPL) dentro de un WebView.
 * Todo se sirve desde los assets de la app, así que funciona sin Internet.
 * La unidad C: se guarda en IndexedDB del WebView y persiste entre sesiones.
 */
public class DosActivity extends Activity {
    private static final String HOST = "https://appassets.androidplatform.net/";
    private static final int REQ_IMPORT = 7;

    private WebView web;
    private File importDir;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        importDir = new File(getFilesDir(), "dos_import");

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        web.setBackgroundColor(0xFF111111);
        web.addJavascriptInterface(new Bridge(), "Android");
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
        });
        setContentView(web);
        web.loadUrl(HOST + "dos/index.html");
    }

    /** Sirve los archivos de assets/dos y los archivos importados. */
    private WebResourceResponse serve(Uri uri) {
        String url = uri.toString();
        if (!url.startsWith(HOST)) return null;
        String path = uri.getPath();
        if (path == null) return null;
        try {
            if (path.startsWith("/dos/")) {
                InputStream in = getAssets().open(path.substring(1));
                return new WebResourceResponse(mime(path), "UTF-8", in);
            }
            if (path.startsWith("/import/")) {
                File f = new File(importDir, Uri.decode(path.substring("/import/".length())));
                if (f.getCanonicalPath().startsWith(importDir.getCanonicalPath()) && f.isFile()) {
                    return new WebResourceResponse("application/octet-stream", null, new FileInputStream(f));
                }
            }
        } catch (Exception ignored) {
        }
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not found",
                null, new java.io.ByteArrayInputStream(new byte[0]));
    }

    private static String mime(String p) {
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".wasm")) return "application/wasm";
        if (p.endsWith(".zip")) return "application/zip";
        return "application/octet-stream";
    }

    /** Métodos accesibles desde la página como window.Android. */
    private class Bridge {
        @JavascriptInterface
        public String getImports() {
            JSONArray arr = new JSONArray();
            List<String> files = new ArrayList<String>();
            list(importDir, "", files);
            for (String f : files) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("path", f);
                    o.put("url", HOST + "import/" + Uri.encode(f, "/"));
                    arr.put(o);
                } catch (Exception ignored) {}
            }
            return arr.toString();
        }

        @JavascriptInterface
        public void importsDone() {
            main.post(new Runnable() {
                @Override public void run() {
                    deleteTree(importDir);
                    Toast.makeText(DosActivity.this, "Archivos copiados a C:\\", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void showKeyboard() {
            main.post(new Runnable() {
                @Override public void run() {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(web, InputMethodManager.SHOW_IMPLICIT);
                }
            });
        }
    }

    private static void list(File dir, String prefix, List<String> out) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) list(f, prefix + f.getName() + "/", out);
            else out.add(prefix + f.getName());
        }
    }

    private static void deleteTree(File f) {
        File[] fs = f.listFiles();
        if (fs != null) for (File c : fs) deleteTree(c);
        f.delete();
    }

    // ------------------------------------------------------------ importar

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Importar programa (ZIP o archivo)");
        menu.add(0, 2, 0, "Reiniciar DOS");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(Intent.createChooser(i, "Elige archivos para C:\\"), REQ_IMPORT);
            return true;
        }
        if (item.getItemId() == 2) {
            restart();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK || data == null) return;
        final List<Uri> uris = new ArrayList<Uri>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) return;
        final ProgressDialog pd = ProgressDialog.show(this, null, "Preparando archivos…", true);
        new Thread(new Runnable() {
            @Override public void run() {
                int count = 0;
                String error = null;
                try {
                    for (Uri u : uris) count += importUri(u);
                } catch (Exception e) {
                    error = e.getMessage();
                }
                final int n = count;
                final String err = error;
                main.post(new Runnable() {
                    @Override public void run() {
                        pd.dismiss();
                        if (err != null) Toast.makeText(DosActivity.this, "Error al importar: " + err, Toast.LENGTH_LONG).show();
                        if (n > 0) {
                            syncThen(new Runnable() { @Override public void run() { restart(); } });
                        }
                    }
                });
            }
        }).start();
    }

    /** Copia el archivo (o el contenido del ZIP) a la carpeta de importación. Nombres en MAYÚSCULAS, estilo DOS. */
    private int importUri(Uri uri) throws Exception {
        String name = displayName(uri);
        importDir.mkdirs();
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            int n = 0;
            ZipInputStream zin = new ZipInputStream(getContentResolver().openInputStream(uri));
            try {
                ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;
                    String clean = e.getName().replace('\\', '/');
                    if (clean.contains("..")) continue;
                    File out = new File(importDir, clean.toUpperCase(Locale.ROOT));
                    out.getParentFile().mkdirs();
                    copy(zin, out, false);
                    n++;
                }
            } finally {
                zin.close();
            }
            return n;
        }
        InputStream in = getContentResolver().openInputStream(uri);
        copy(in, new File(importDir, name.toUpperCase(Locale.ROOT)), true);
        return 1;
    }

    private String displayName(Uri uri) {
        String name = null;
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        if (name == null) name = uri.getLastPathSegment();
        if (name == null) name = "ARCHIVO.BIN";
        return name.replace('/', '_').replace(' ', '_');
    }

    private static void copy(InputStream in, File out, boolean closeIn) throws Exception {
        OutputStream os = new FileOutputStream(out);
        try {
            byte[] b = new byte[64 * 1024];
            int n;
            while ((n = in.read(b)) > 0) os.write(b, 0, n);
        } finally {
            os.close();
            if (closeIn) in.close();
        }
    }

    /** Pide a la página que guarde C: en IndexedDB y luego ejecuta la acción. */
    private void syncThen(final Runnable after) {
        web.evaluateJavascript("window.dosfs && dosfs.fs.syncfs(false, function(){});", null);
        main.postDelayed(after, 600);
    }

    private void restart() {
        web.loadUrl("about:blank");
        web.loadUrl(HOST + "dos/index.html");
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.evaluateJavascript("window.dosfs && dosfs.fs.syncfs(false, function(){});", null);
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
