package com.thanhman5.todolist;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String HOST = "checklist.local";
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private static final int FILE_REQ = 42;

    // ---- localStorage backup ----------------------------------------------
    // The app keeps all of its data in localStorage. A script injected into
    // index.html (before any app code runs) restores missing keys from the
    // native backup file and snapshots every key back to it every 20s and
    // whenever the page is hidden, so data survives a WebView storage reset
    // and sudden process death.
    private File backupFile() {
        return new File(new File(getFilesDir(), "save-backup"), "localstorage.json");
    }
    private File backupFileExternal() {
        File ext = getExternalFilesDir(null);
        if (ext == null) return null;
        return new File(new File(ext, "save-backup"), "localstorage.json");
    }
    private static void writeAtomic(File f, byte[] data) throws Exception {
        f.getParentFile().mkdirs();
        File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
        FileOutputStream os = new FileOutputStream(tmp);
        os.write(data); os.flush(); os.close();
        if (f.exists()) f.delete();
        tmp.renameTo(f);
    }
    private static String readAll(File f) {
        try {
            if (f == null || !f.exists()) return "";
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return bos.toString("UTF-8");
        } catch (Exception e) { return ""; }
    }

    public class BackupBridge {
        @JavascriptInterface
        public String load() {
            String s = readAll(backupFile());
            if (s.isEmpty()) s = readAll(backupFileExternal());
            return s;
        }
        @JavascriptInterface
        public void save(String json) {
            try {
                if (json == null || json.length() < 2 || json.length() > 24 * 1024 * 1024) return;
                byte[] data = json.getBytes(StandardCharsets.UTF_8);
                writeAtomic(backupFile(), data);
                File ext = backupFileExternal();
                if (ext != null) {
                    try { writeAtomic(ext, data); } catch (Exception ignored) { }
                }
            } catch (Exception ignored) { }
        }
    }

    // Bridge used by the injected helper to save exported backup files (blob downloads).
    public class SaveBridge {
        @JavascriptInterface
        public void save(String filename, String base64) {
            try {
                byte[] data = Base64.decode(base64, Base64.DEFAULT);
                File dir = new File(getExternalFilesDir(null), "exports");
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, filename);
                OutputStream os = new FileOutputStream(out);
                os.write(data); os.close();
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Da luu: " + out.getAbsolutePath(), Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Luu file loi: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }
    }

    private static final String BOOTSTRAP =
        "<script>(function(){try{if(!window.AndroidBackup)return;" +
        "var raw=window.AndroidBackup.load();" +
        "if(raw){try{var o=JSON.parse(raw);for(var k in o){try{if(localStorage.getItem(k)===null)localStorage.setItem(k,o[k]);}catch(e){}}}catch(e){}}" +
        "var last='';" +
        "var snap=function(){try{var o={},n=0;for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);" +
        "if(k){o[k]=localStorage.getItem(k);n++;}}" +
        "if(!n)return;var j=JSON.stringify(o);if(j===last)return;last=j;window.AndroidBackup.save(j);}catch(e){}};" +
        "window.__apkSnap=snap;" +
        "setInterval(snap,20000);" +
        "document.addEventListener('visibilitychange',function(){if(document.visibilityState==='hidden')snap();});" +
        "window.addEventListener('pagehide',snap);" +
        "setTimeout(snap,5000);" +
        "}catch(e){}})();</script>";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(false);
        s.setTextZoom(100);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        web.addJavascriptInterface(new SaveBridge(), "AndroidSave");
        web.addJavascriptInterface(new BackupBridge(), "AndroidBackup");
        web.setWebViewClient(new LocalClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb,
                                             FileChooserParams params) {
                fileCallback = cb;
                try {
                    Intent i = params.createIntent();
                    startActivityForResult(i, FILE_REQ);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });
        web.loadUrl("https://" + HOST + "/index.html");
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (web != null) web.evaluateJavascript(
                    "window.__apkSnap&&window.__apkSnap()", null);
            CookieManager.getInstance().flush();
        } catch (Exception ignored) { }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == FILE_REQ && fileCallback != null) {
            fileCallback.onReceiveValue(
                    WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    private static String mime(String p) {
        String l = p.toLowerCase();
        if (l.endsWith(".html")) return "text/html";
        if (l.endsWith(".js") || l.endsWith(".mjs")) return "text/javascript";
        if (l.endsWith(".css")) return "text/css";
        if (l.endsWith(".json")) return "application/json";
        if (l.endsWith(".png")) return "image/png";
        if (l.endsWith(".jpg") || l.endsWith(".jpeg")) return "image/jpeg";
        if (l.endsWith(".webp")) return "image/webp";
        if (l.endsWith(".svg")) return "image/svg+xml";
        if (l.endsWith(".ico")) return "image/x-icon";
        if (l.endsWith(".ttf")) return "font/ttf";
        if (l.endsWith(".woff2")) return "font/woff2";
        if (l.endsWith(".woff")) return "font/woff";
        if (l.endsWith(".mp3")) return "audio/mpeg";
        if (l.endsWith(".ogg")) return "audio/ogg";
        if (l.endsWith(".wav")) return "audio/wav";
        if (l.endsWith(".webmanifest")) return "application/manifest+json";
        return "application/octet-stream";
    }

    private WebResourceResponse serveIndex(Map<String, String> headers) {
        try {
            InputStream in = getAssets().open("www/index.html");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            String html = bos.toString("UTF-8");
            int headEnd = html.indexOf("<head>");
            if (headEnd >= 0) {
                int at = headEnd + "<head>".length();
                html = html.substring(0, at) + BOOTSTRAP + html.substring(at);
            } else {
                html = BOOTSTRAP + html;
            }
            return new WebResourceResponse("text/html", "UTF-8", 200, "OK",
                    headers, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                    headers, new ByteArrayInputStream(new byte[0]));
        }
    }

    private class LocalClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            if (u != null && HOST.equals(u.getHost())) return false;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, u));
            } catch (Exception ignored) { }
            return true;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            if (u == null || !HOST.equals(u.getHost())) return null;
            String path = u.getPath();
            if (path == null || path.equals("/")) path = "/index.html";

            Map<String, String> headers = new HashMap<>();
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Cache-Control", "no-cache");

            if (path.equals("/index.html")) return serveIndex(headers);
            try {
                InputStream in = getAssets().open("www" + path);
                return new WebResourceResponse(mime(path), path.endsWith(".html")
                        || path.endsWith(".js") || path.endsWith(".css")
                        || path.endsWith(".json") || path.endsWith(".svg")
                        ? "UTF-8" : null, 200, "OK", headers, in);
            } catch (Exception e) {
                return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                        headers, new ByteArrayInputStream(new byte[0]));
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            // Route blob: downloads (app backup export) through the native bridge.
            view.evaluateJavascript(
                "(function(){if(window.__apkDl)return;window.__apkDl=1;" +
                "document.addEventListener('click',function(e){var a=e.target.closest&&e.target.closest('a[download]');" +
                "if(!a||!a.href||a.href.indexOf('blob:')!==0)return;e.preventDefault();" +
                "fetch(a.href).then(function(r){return r.blob();}).then(function(b){var fr=new FileReader();" +
                "fr.onload=function(){try{window.AndroidSave.save(a.download||'checklist-backup.json',String(fr.result).split(',')[1]);}catch(_){}};" +
                "fr.readAsDataURL(b);}).catch(function(){});},true);})();", null);
        }
    }
}
