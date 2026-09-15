package com.astra.visitlog;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.database.Cursor;
import android.util.Base64;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int LOCATION_REQ = 2001;
    private static final int FILE_REQ = 2002;
    private WebView webView;
    private ValueCallback<Uri[]> fileChooserCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setGeolocationEnabled(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient());

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false);
                } else {
                    requestPermissions(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }, LOCATION_REQ);
                    callback.invoke(origin, true, false);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                try {
                    startActivityForResult(intent, FILE_REQ);
                } catch (Exception e) {
                    fileChooserCallback = null;
                    return false;
                }
                return true;
            }
        });

        // Fallback for normal downloads. Blob exports are handled by the JS bridge below.
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimetype, long contentLength) {
                Toast.makeText(MainActivity.this,
                        "This file is generated inside ASTRA. Use Export/Backup in the app.",
                        Toast.LENGTH_LONG).show();
            }
        });

        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.loadUrl("file:///android_asset/index.html");

        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, LOCATION_REQ);
        }
    }

    private void saveBytes(byte[] bytes, String filename, String mime) throws Exception {
        String safeName = filename == null ? "ASTRA_file" : filename.replaceAll("[\\/:*?\"<>|]", "_");
        String safeMime = (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime;
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
            values.put(MediaStore.Downloads.MIME_TYPE, safeMime);
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ASTRA Visit Log");
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("Could not create Downloads file");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) { if (out == null) throw new Exception("Could not open output stream"); out.write(bytes); }
            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0); getContentResolver().update(uri, values, null, null);
        } else {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 3001);
                throw new Exception("Storage permission is required to save the file.");
            }
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ASTRA Visit Log");
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("Cannot create Downloads folder");
            try (FileOutputStream out = new FileOutputStream(new File(dir, safeName))) { out.write(bytes); }
        }
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void openExternalUrl(String url) {
            try {
                String value = url == null ? "" : url.trim();
                if (!value.matches("(?i)^https?://.+")) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "Invalid route link.", Toast.LENGTH_SHORT).show());
                    return;
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(value));
                startActivity(intent);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Could not open the route. Please check the Google Maps link.", Toast.LENGTH_LONG).show());
            }
        }

        @JavascriptInterface
        public void saveBase64File(String dataUrl, String filename, String mime) {
            try {
                String encoded = dataUrl;
                int comma = encoded.indexOf(',');
                if (comma >= 0) encoded = encoded.substring(comma + 1);
                byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
                String safeName = filename == null ? "ASTRA_file" : filename.replaceAll("[\\\\/:*?\"<>|]", "_");
                String safeMime = (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime;

                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                    values.put(MediaStore.Downloads.MIME_TYPE, safeMime);
                    values.put(MediaStore.Downloads.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS + "/ASTRA Visit Log");
                    values.put(MediaStore.Downloads.IS_PENDING, 1);

                    Uri uri = getContentResolver().insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new Exception("Could not create Downloads file");

                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out == null) throw new Exception("Could not open output stream");
                        out.write(bytes);
                    }

                    values.clear();
                    values.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(uri, values, null, null);
                } else {
                    if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                "Storage permission is required to save the file.",
                                Toast.LENGTH_LONG).show());
                        requestPermissions(new String[]{
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        }, 3001);
                        return;
                    }
                    File dir = new File(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS), "ASTRA Visit Log");
                    if (!dir.exists() && !dir.mkdirs()) throw new Exception("Cannot create Downloads folder");
                    File file = new File(dir, safeName);
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        out.write(bytes);
                    }
                }

                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Saved to Downloads / ASTRA Visit Log",
                        Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Save failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        }

        @JavascriptInterface
        public void saveBackup(String json, String filename) {
            try {
                org.json.JSONObject check = new org.json.JSONObject(json); if(!check.has("clients") || !check.has("visits")) throw new Exception("Invalid data");
                org.json.JSONObject payload = new org.json.JSONObject();
                payload.put("format", "ASTRA Visit Log Backup"); payload.put("version", 1); payload.put("exportedAt", new java.util.Date().toString()); payload.put("data", new org.json.JSONObject(json));
                saveBytes(payload.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8), filename, "application/json");
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Backup saved to Downloads / ASTRA Visit Log", Toast.LENGTH_LONG).show());
            } catch (Exception e) { runOnUiThread(() -> Toast.makeText(MainActivity.this, "Backup failed: " + e.getMessage(), Toast.LENGTH_LONG).show()); }
        }

        @JavascriptInterface
        public void exportExcel(String json, String filename) {
            try {
                org.json.JSONObject d = new org.json.JSONObject(json);
                byte[] bytes = XlsxNative.buildWorkbook(d);
                saveBytes(bytes, filename, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Excel saved to Downloads / ASTRA Visit Log", Toast.LENGTH_LONG).show());
            } catch (Exception e) { runOnUiThread(() -> Toast.makeText(MainActivity.this, "Excel export failed: " + e.getMessage(), Toast.LENGTH_LONG).show()); }
        }

        @JavascriptInterface
        public void pickFile(String kind) {
            final String selectedKind = kind == null ? "excel" : kind;
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    if ("backup".equals(selectedKind)) {
                        intent.setType("application/json");
                    } else {
                        intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel",
                                "application/octet-stream"
                        });
                    }
                    startActivityForResult(intent, "backup".equals(selectedKind) ? 2101 : 2102);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,
                            "File picker failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) { int i=c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if(i>=0) return c.getString(i); }
        } catch(Exception ignored) {}
        return "ASTRA_import";
    }

    private void deliverPickedFile(Uri uri, String kind) {
        if (uri == null) return;
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) throw new Exception("Cannot read selected file");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(); byte[] tmp=new byte[8192]; int n;
            while((n=in.read(tmp))!=-1) buffer.write(tmp,0,n); in.close();
            String jsonResult;
            if ("backup".equals(kind)) {
                String raw=buffer.toString("UTF-8");
                org.json.JSONObject payload=new org.json.JSONObject(raw);
                org.json.JSONObject incoming=payload.optJSONObject("data");
                if(incoming==null) incoming=payload;
                if(!incoming.has("clients") || !incoming.has("visits")) throw new Exception("Invalid ASTRA backup file");
                jsonResult=incoming.toString();
                String js="window.androidReceiveBackup("+org.json.JSONObject.quote(jsonResult)+");";
                webView.evaluateJavascript(js,null);
            } else {
                org.json.JSONObject parsed=XlsxNative.readWorkbook(buffer.toByteArray());
                jsonResult=parsed.toString();
                String js="window.androidReceiveExcel("+org.json.JSONObject.quote(jsonResult)+");";
                webView.evaluateJavascript(js,null);
            }
        } catch(Exception e) { runOnUiThread(() -> Toast.makeText(MainActivity.this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show()); }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_REQ) {
            if (fileChooserCallback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
                fileChooserCallback.onReceiveValue(results);
                fileChooserCallback = null;
            }
            return;
        }

        if ((requestCode == 2101 || requestCode == 2102) && resultCode == RESULT_OK && data != null) {
            deliverPickedFile(data.getData(), requestCode == 2101 ? "backup" : "excel");
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
