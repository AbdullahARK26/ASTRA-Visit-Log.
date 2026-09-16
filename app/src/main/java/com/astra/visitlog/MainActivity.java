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

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class MainActivity extends Activity {

    private static final int LOCATION_REQ = 2001;
    private static final int FILE_REQ = 2002;

    private static final int GOOGLE_SIGN_IN_REQ = 3003;
    private static final int GOOGLE_RECOVERABLE_REQ = 3004;

    private static final String GOOGLE_CLIENT_ID =
            "570018297921-u334aq6q3qa6iu9m5vqh98fu91c9ncbd.apps.googleusercontent.com";

    private static final String DRIVE_SCOPE =
            "https://www.googleapis.com/auth/drive.appdata";

    private static final String BACKUP_FILE_NAME =
            "ARK_Visit_Log_Backup.json";

    private static final String DRIVE_FILES_URL =
            "https://www.googleapis.com/drive/v3/files";

    private static final String DRIVE_UPLOAD_URL =
            "https://www.googleapis.com/upload/drive/v3/files";

    private WebView webView;
    private ValueCallback<Uri[]> fileChooserCallback;

    private GoogleSignInClient googleSignInClient;
    private GoogleSignInAccount googleAccount;

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
            public void onGeolocationPermissionsShowPrompt(
                    String origin,
                    GeolocationPermissions.Callback callback) {

                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                        || checkSelfPermission(
                        Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {

                    callback.invoke(origin, true, false);

                } else {

                    requestPermissions(
                            new String[]{
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                            },
                            LOCATION_REQ
                    );

                    callback.invoke(origin, true, false);
                }
            }

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {

                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                }

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

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(
                    String url,
                    String userAgent,
                    String contentDisposition,
                    String mimetype,
                    long contentLength) {

                Toast.makeText(
                        MainActivity.this,
                        "This file is generated inside ARK. Use Export/Backup in the app.",
                        Toast.LENGTH_LONG
                ).show();
            }
        });

        webView.addJavascriptInterface(new AndroidBridge(), "Android");

        webView.loadUrl("file:///android_asset/index.html");

        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(
                        Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_REQ
            );
        }

        initializeGoogleSignIn();
    }

    // ============================================================
    // GOOGLE SIGN-IN
    // ============================================================

    private void initializeGoogleSignIn() {

        GoogleSignInOptions gso =
                new GoogleSignInOptions.Builder(
                        GoogleSignInOptions.DEFAULT_SIGN_IN)

                        .requestEmail()

                        .requestScopes(
                                new Scope(DRIVE_SCOPE)
                        )

                        .build();

        googleSignInClient =
                GoogleSignIn.getClient(this, gso);

        googleAccount =
                GoogleSignIn.getLastSignedInAccount(this);

        if (googleAccount != null) {
            notifyWebViewGoogleAccount();
        }
    }

    private void startGoogleSignIn() {

        if (googleSignInClient == null) {
            initializeGoogleSignIn();
        }

        Intent signInIntent =
                googleSignInClient.getSignInIntent();

        startActivityForResult(
                signInIntent,
                GOOGLE_SIGN_IN_REQ
        );
    }

    private void notifyWebViewGoogleAccount() {

        if (googleAccount == null) {
            return;
        }

        String email = googleAccount.getEmail();

        if (email == null) {
            email = "";
        }

        final String safeEmail =
                JSONObject.quote(email);

        runOnUiThread(() -> {

            try {
                webView.evaluateJavascript(
                        "if(window.androidGoogleSignedIn)" +
                                "{window.androidGoogleSignedIn(" +
                                safeEmail +
                                ");}",
                        null
                );
            } catch (Exception ignored) {
            }

        });
    }

    private void notifyWebViewGoogleSignedOut() {

        runOnUiThread(() -> {

            try {
                webView.evaluateJavascript(
                        "if(window.androidGoogleSignedOut)" +
                                "{window.androidGoogleSignedOut();}",
                        null
                );
            } catch (Exception ignored) {
            }

        });
    }

    // ============================================================
    // LOCAL FILE STORAGE
    // ============================================================

    private void saveBytes(
            byte[] bytes,
            String filename,
            String mime) throws Exception {

        String safeName =
                filename == null
                        ? "ARK_file"
                        : filename.replaceAll(
                        "[\\\\/:*?\"<>|]",
                        "_"
                );

        String safeMime =
                (mime == null || mime.isEmpty())
                        ? "application/octet-stream"
                        : mime;

        if (Build.VERSION.SDK_INT >= 29) {

            ContentValues values =
                    new ContentValues();

            values.put(
                    MediaStore.Downloads.DISPLAY_NAME,
                    safeName
            );

            values.put(
                    MediaStore.Downloads.MIME_TYPE,
                    safeMime
            );

            values.put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS
                            + "/ARK Visit Log"
            );

            values.put(
                    MediaStore.Downloads.IS_PENDING,
                    1
            );

            Uri uri =
                    getContentResolver().insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            values
                    );

            if (uri == null) {
                throw new Exception(
                        "Could not create Downloads file"
                );
            }

            try (OutputStream out =
                         getContentResolver().openOutputStream(uri)) {

                if (out == null) {
                    throw new Exception(
                            "Could not open output stream"
                    );
                }

                out.write(bytes);
            }

            values.clear();

            values.put(
                    MediaStore.Downloads.IS_PENDING,
                    0
            );

            getContentResolver().update(
                    uri,
                    values,
                    null,
                    null
            );

        } else {

            if (checkSelfPermission(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        3001
                );

                throw new Exception(
                        "Storage permission is required to save the file."
                );
            }

            File dir =
                    new File(
                            Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_DOWNLOADS
                            ),
                            "ARK Visit Log"
                    );

            if (!dir.exists() && !dir.mkdirs()) {
                throw new Exception(
                        "Cannot create Downloads folder"
                );
            }

            try (FileOutputStream out =
                         new FileOutputStream(
                                 new File(dir, safeName)
                         )) {

                out.write(bytes);
            }
        }
    }

    // ============================================================
    // GOOGLE DRIVE HELPERS
    // ============================================================

    private String getGoogleAccessToken()
            throws Exception {

        if (googleAccount == null) {
            throw new Exception(
                    "Please sign in with your Google Account first."
            );
        }

        if (googleAccount.getAccount() == null) {
            throw new Exception(
                    "Google Account is unavailable."
            );
        }

        String scope =
                "oauth2:" + DRIVE_SCOPE;

        try {

            return GoogleAuthUtil.getToken(
                    this,
                    googleAccount.getAccount(),
                    scope
            );

        } catch (UserRecoverableAuthException e) {

            runOnUiThread(() -> {

                try {
                    startActivityForResult(
                            e.getIntent(),
                            GOOGLE_RECOVERABLE_REQ
                    );
                } catch (Exception ignored) {
                }

            });

            throw new Exception(
                    "Google permission is required."
            );
        }
    }

    private HttpURLConnection openDriveConnection(
            String urlString,
            String method,
            String accessToken) throws Exception {

        URL url = new URL(urlString);

        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

        connection.setRequestMethod(method);
        connection.setRequestProperty(
                "Authorization",
                "Bearer " + accessToken
        );

        connection.setRequestProperty(
                "Accept",
                "application/json"
        );

        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);

        return connection;
    }

    private String readConnection(
            HttpURLConnection connection)
            throws Exception {

        InputStream input;

        if (connection.getResponseCode() >= 400) {
            input = connection.getErrorStream();
        } else {
            input = connection.getInputStream();
        }

        if (input == null) {
            return "";
        }

        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();

        byte[] temp = new byte[8192];

        int n;

        while ((n = input.read(temp)) != -1) {
            buffer.write(temp, 0, n);
        }

        input.close();

        return buffer.toString(
                StandardCharsets.UTF_8.name()
        );
    }

    private String findDriveBackupId(
            String accessToken) throws Exception {

        String query =
                "name='" +
                        BACKUP_FILE_NAME +
                        "' and trashed=false";

        String url =
                DRIVE_FILES_URL
                        + "?spaces=appDataFolder"
                        + "&fields=files(id,name)"
                        + "&q="
                        + URLEncoder.encode(
                        query,
                        "UTF-8"
                );

        HttpURLConnection connection =
                openDriveConnection(
                        url,
                        "GET",
                        accessToken
                );

        int responseCode =
                connection.getResponseCode();

        String response =
                readConnection(connection);

        connection.disconnect();

        if (responseCode < 200 ||
                responseCode >= 300) {

            throw new Exception(
                    "Google Drive search failed: "
                            + response
            );
        }

        JSONObject result =
                new JSONObject(response);

        JSONArray files =
                result.optJSONArray("files");

        if (files != null &&
                files.length() > 0) {

            return files
                    .getJSONObject(0)
                    .optString("id", "");
        }

        return "";
    }

    private void uploadBackupToDrive(
            String json) throws Exception {

        String accessToken =
                getGoogleAccessToken();

        String existingId =
                findDriveBackupId(accessToken);

        if (existingId != null &&
                !existingId.isEmpty()) {

            String url =
                    DRIVE_UPLOAD_URL
                            + "/"
                            + URLEncoder.encode(
                            existingId,
                            "UTF-8"
                    )
                            + "?uploadType=media";

            HttpURLConnection connection =
                    openDriveConnection(
                            url,
                            "PATCH",
                            accessToken
                    );

            connection.setDoOutput(true);

            connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
            );

            byte[] bytes =
                    json.getBytes(
                            StandardCharsets.UTF_8
                    );

            connection.setFixedLengthStreamingMode(
                    bytes.length
            );

            try (OutputStream out =
                         connection.getOutputStream()) {

                out.write(bytes);
            }

            int responseCode =
                    connection.getResponseCode();

            String response =
                    readConnection(connection);

            connection.disconnect();

            if (responseCode < 200 ||
                    responseCode >= 300) {

                throw new Exception(
                        "Drive backup update failed: "
                                + response
                );
            }

        } else {

            String boundary =
                    "ARKBoundary"
                            + System.currentTimeMillis();

            String metadata =
                    "{"
                            + "\"name\":\""
                            + BACKUP_FILE_NAME
                            + "\","
                            + "\"parents\":[\"appDataFolder\"],"
                            + "\"mimeType\":\"application/json\""
                            + "}";

            String url =
                    DRIVE_UPLOAD_URL
                            + "?uploadType=multipart";

            HttpURLConnection connection =
                    openDriveConnection(
                            url,
                            "POST",
                            accessToken
                    );

            connection.setDoOutput(true);

            connection.setRequestProperty(
                    "Content-Type",
                    "multipart/related; boundary="
                            + boundary
            );

            byte[] jsonBytes =
                    json.getBytes(
                            StandardCharsets.UTF_8
                    );

            try (OutputStream out =
                         connection.getOutputStream()) {

                String part1 =
                        "--" + boundary + "\r\n"
                                + "Content-Type: application/json; charset=UTF-8\r\n"
                                + "\r\n"
                                + metadata
                                + "\r\n";

                out.write(
                        part1.getBytes(
                                StandardCharsets.UTF_8
                        )
                );

                String part2 =
                        "--" + boundary + "\r\n"
                                + "Content-Type: application/json\r\n"
                                + "\r\n";

                out.write(
                        part2.getBytes(
                                StandardCharsets.UTF_8
                        )
                );

                out.write(jsonBytes);

                out.write(
                        ("\r\n--"
                                + boundary
                                + "--\r\n")
                                .getBytes(
                                        StandardCharsets.UTF_8
                                )
                );
            }

            int responseCode =
                    connection.getResponseCode();

            String response =
                    readConnection(connection);

            connection.disconnect();

            if (responseCode < 200 ||
                    responseCode >= 300) {

                throw new Exception(
                        "Drive backup failed: "
                                + response
                );
            }
        }
    }

    private String downloadBackupFromDrive()
            throws Exception {

        String accessToken =
                getGoogleAccessToken();

        String fileId =
                findDriveBackupId(accessToken);

        if (fileId == null ||
                fileId.isEmpty()) {

            throw new Exception(
                    "No ARK backup was found in your Google Account."
            );
        }

        String url =
                DRIVE_FILES_URL
                        + "/"
                        + URLEncoder.encode(
                        fileId,
                        "UTF-8"
                )
                        + "?alt=media";

        HttpURLConnection connection =
                openDriveConnection(
                        url,
                        "GET",
                        accessToken
                );

        int responseCode =
                connection.getResponseCode();

        String response =
                readConnection(connection);

        connection.disconnect();

        if (responseCode < 200 ||
                responseCode >= 300) {

            throw new Exception(
                    "Drive restore failed: "
                            + response
            );
        }

        return response;
    }

    // ============================================================
    // JAVASCRIPT BRIDGE
    // ============================================================

    public class AndroidBridge {

        @JavascriptInterface
        public void googleSignIn() {

            runOnUiThread(() -> {

                try {
                    startGoogleSignIn();
                } catch (Exception e) {

                    Toast.makeText(
                            MainActivity.this,
                            "Google sign-in failed: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                }

            });
        }

        @JavascriptInterface
        public void googleSignOut() {

            runOnUiThread(() -> {

                if (googleSignInClient == null) {
                    initializeGoogleSignIn();
                }

                googleSignInClient.signOut()
                        .addOnCompleteListener(task -> {

                            googleAccount = null;

                            notifyWebViewGoogleSignedOut();

                            Toast.makeText(
                                    MainActivity.this,
                                    "Google Account signed out.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
            });
        }

        @JavascriptInterface
        public void googleBackup(String json) {

            if (json == null ||
                    json.trim().isEmpty()) {

                Toast.makeText(
                        MainActivity.this,
                        "Nothing to back up.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            new Thread(() -> {

                try {

                    JSONObject check =
                            new JSONObject(json);

                    if (!check.has("clients") ||
                            !check.has("visits")) {

                        throw new Exception(
                                "Invalid ARK data."
                        );
                    }

                    JSONObject payload =
                            new JSONObject();

                    payload.put(
                            "format",
                            "ARK Visit Log Backup"
                    );

                    payload.put(
                            "version",
                            1
                    );

                    payload.put(
                            "exportedAt",
                            new Date().toString()
                    );

                    payload.put(
                            "data",
                            check
                    );

                    uploadBackupToDrive(
                            payload.toString()
                    );

                    runOnUiThread(() ->
                            Toast.makeText(
                                    MainActivity.this,
                                    "ARK backup saved to Google Drive.",
                                    Toast.LENGTH_LONG
                            ).show()
                    );

                } catch (Exception e) {

                    runOnUiThread(() ->
                            Toast.makeText(
                                    MainActivity.this,
                                    "Google Drive backup failed: "
                                            + e.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show()
                    );
                }

            }).start();
        }

        @JavascriptInterface
        public void googleRestore() {

            new Thread(() -> {

                try {

                    String raw =
                            downloadBackupFromDrive();

                    JSONObject payload =
                            new JSONObject(raw);

                    JSONObject incoming =
                            payload.optJSONObject("data");

                    if (incoming == null) {
                        incoming = payload;
                    }

                    if (!incoming.has("clients") ||
                            !incoming.has("visits")) {

                        throw new Exception(
                                "Invalid ARK Drive backup."
                        );
                    }

                    String jsonResult =
                            incoming.toString();

                    String js =
                            "if(window.androidReceiveBackup)"
                                    + "{window.androidReceiveBackup("
                                    + JSONObject.quote(jsonResult)
                                    + ");}";

                    final String finalJs = js;

                    runOnUiThread(() -> {

                        webView.evaluateJavascript(
                                finalJs,
                                null
                        );

                        Toast.makeText(
                                MainActivity.this,
                                "ARK data restored from Google Drive.",
                                Toast.LENGTH_LONG
                        ).show();
                    });

                } catch (Exception e) {

                    runOnUiThread(() ->
                            Toast.makeText(
                                    MainActivity.this,
                                    "Google Drive restore failed: "
                                            + e.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show()
                    );
                }

            }).start();
        }

        @JavascriptInterface
        public void googleAccountStatus() {

            runOnUiThread(() -> {

                googleAccount =
                        GoogleSignIn.getLastSignedInAccount(
                                MainActivity.this
                        );

                if (googleAccount != null) {
                    notifyWebViewGoogleAccount();
                }

            });
        }

        // --------------------------------------------------------
        // Existing route functionality
        // --------------------------------------------------------

        @JavascriptInterface
        public void openExternalUrl(String url) {

            try {

                String value =
                        url == null
                                ? ""
                                : url.trim();

                if (!value.matches(
                        "(?i)^https?://.+")) {

                    runOnUiThread(() ->
                            Toast.makeText(
                                    MainActivity.this,
                                    "Invalid route link.",
                                    Toast.LENGTH_SHORT
                            ).show()
                    );

                    return;
                }

                Intent intent =
                        new Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(value)
                        );

                startActivity(intent);

            } catch (Exception e) {

                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                "Could not open the route. Please check the Google Maps link.",
                                Toast.LENGTH_LONG
                        ).show()
                );
            }
        }

        // --------------------------------------------------------
        // Existing file save functionality
        // --------------------------------------------------------

        @JavascriptInterface
        public void saveBase64File(
                String dataUrl,
                String filename,
                String mime) {

            try {

                String encoded =
                        dataUrl;

                int comma =
                        encoded.indexOf(',');

                if (comma >= 0) {
                    encoded =
                            encoded.substring(
                                    comma + 1
                            );
                }

                byte[] bytes =
                        Base64.decode(
                                encoded,
                                Base64.DEFAULT
                        );

                String safeName =
                        filename == null
                                ? "ARK_file"
                                : filename.replaceAll(
                                "[\\\\/:*?\"<>|]",
                                "_"
                        );

                String safeMime =
                        (mime == null ||
                                mime.isEmpty())
                                ? "application/octet-stream"
                                : mime;

                saveBytes(
                        bytes,
                        safeName,
                        safeMime
                );

                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                "Saved to Downloads / ARK Visit Log",
                                Toast.LENGTH_LONG
                        ).show()
                );

            } catch (Exception e) {

                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                "Save failed: "
                                        + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
            }
        }

        // --------------------------------------------------------
        // Existing JSON backup
        // --------------------------------------------------------

        @JavascriptInterface
        public void saveBackup(
                String json,
                String filename) {

            try {

                JSONObject check =
                        new JSONObject(json);

                if (!check.has("clients") ||
                        !check.has("visits")) {

                    throw new Exception(
                            "Invalid data"
                    );
                }

                JSONObject payload =
                        new JSONObject();

                payload.put(
                        "format",
                        "ARK Visit Log Backup"
                );

                payload.put(
                        "version",
                        1
                );

                payload.put(
                        "exportedAt",
                        new Date().toString()
                );

                payload.put(
                        "data",
                        check
                );

                saveBytes(
                        payload.toString(2)
                                .getBytes(
                                        StandardCharsets.UTF_8
                                ),
                        filename,
                        "application/json"
                );

                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                "Backup saved to Downloads / ARK Visit Log",
                                Toast.LENGTH_LONG
                        ).show()
                );

            } catch (Exception e) {

                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                "Backup failed: "
                                        + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
            }
        }

        // --------------------------------------------------------
        // Existing Excel export
        // --------------------------------------------------------

        @JavascriptInterface
        public void exportExcel(
                String json,
                String filename) {

            try {

                JSONObject d =
                        new JSONObject(json);

                byte[] bytes =
                        XlsxNative.buildWorkbook(d);

                saveBytes(
                        bytes,
                        filename,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                );

                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                "Excel saved to Downloads / ARK Visit Log",
                                Toast.LENGTH_LONG
                        ).show()
                );

            } catch (Exception e) {

                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                "Excel export failed: "
                                        + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
            }
        }

        // --------------------------------------------------------
        // Existing file picker
        // --------------------------------------------------------

        @JavascriptInterface
        public void pickFile(String kind) {

            final String selectedKind =
                    kind == null
                            ? "excel"
                            : kind;

            runOnUiThread(() -> {

                try {

                    Intent intent =
                            new Intent(
                                    Intent.ACTION_OPEN_DOCUMENT
                            );

                    intent.addCategory(
                            Intent.CATEGORY_OPENABLE
                    );

                    if ("backup".equals(
                            selectedKind)) {

                        intent.setType(
                                "application/json"
                        );

                    } else {

                        intent.setType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        );

                        intent.putExtra(
                                Intent.EXTRA_MIME_TYPES,
                                new String[]{
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/vnd.ms-excel",
                                        "application/octet-stream"
                                }
                        );
                    }

                    startActivityForResult(
                            intent,
                            "backup".equals(
                                    selectedKind)
                                    ? 2101
                                    : 2102
                    );

                } catch (Exception e) {

                    Toast.makeText(
                            MainActivity.this,
                            "File picker failed: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
        }
    }

    // ============================================================
    // FILE IMPORT
    // ============================================================

    private String displayName(Uri uri) {

        try (Cursor c =
                     getContentResolver().query(
                             uri,
                             null,
                             null,
                             null,
                             null
                     )) {

            if (c != null &&
                    c.moveToFirst()) {

                int i =
                        c.getColumnIndex(
                                android.provider.OpenableColumns.DISPLAY_NAME
                        );

                if (i >= 0) {
                    return c.getString(i);
                }
            }

        } catch (Exception ignored) {
        }

        return "ARK_import";
    }

    private void deliverPickedFile(
            Uri uri,
            String kind) {

        if (uri == null) {
            return;
        }

        try {

            InputStream in =
                    getContentResolver()
                            .openInputStream(uri);

            if (in == null) {
                throw new Exception(
                        "Cannot read selected file"
                );
            }

            ByteArrayOutputStream buffer =
                    new ByteArrayOutputStream();

            byte[] tmp =
                    new byte[8192];

            int n;

            while ((n =
                    in.read(tmp)) != -1) {

                buffer.write(
                        tmp,
                        0,
                        n
                );
            }

            in.close();

            String jsonResult;

            if ("backup".equals(kind)) {

                String raw =
                        buffer.toString("UTF-8");

                JSONObject payload =
                        new JSONObject(raw);

                JSONObject incoming =
                        payload.optJSONObject(
                                "data"
                        );

                if (incoming == null) {
                    incoming = payload;
                }

                if (!incoming.has("clients") ||
                        !incoming.has("visits")) {

                    throw new Exception(
                            "Invalid ARK backup file"
                    );
                }

                jsonResult =
                        incoming.toString();

                String js =
                        "window.androidReceiveBackup("
                                + JSONObject.quote(
                                jsonResult
                        )
                                + ");";

                webView.evaluateJavascript(
                        js,
                        null
                );

            } else {

                JSONObject parsed =
                        XlsxNative.readWorkbook(
                                buffer.toByteArray()
                        );

                jsonResult =
                        parsed.toString();

                String js =
                        "window.androidReceiveExcel("
                                + JSONObject.quote(
                                jsonResult
                        )
                                + ");";

                webView.evaluateJavascript(
                        js,
                        null
                );
            }

        } catch (Exception e) {

            runOnUiThread(() ->
                    Toast.makeText(
                            MainActivity.this,
                            "Import failed: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show()
            );
        }
    }

    // ============================================================
    // ACTIVITY RESULTS
    // ============================================================

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        // --------------------------------------------------------
        // Google Sign-In
        // --------------------------------------------------------

        if (requestCode ==
                GOOGLE_SIGN_IN_REQ) {

            if (resultCode ==
                    RESULT_OK &&
                    data != null) {

                Task<GoogleSignInAccount> task =
                        GoogleSignIn.getSignedInAccountFromIntent(
                                data
                        );

                try {

                    googleAccount =
                            task.getResult(
                                    ApiException.class
                            );

                    notifyWebViewGoogleAccount();

                    Toast.makeText(
                            MainActivity.this,
                            "Google Account connected: "
                                    + googleAccount.getEmail(),
                            Toast.LENGTH_LONG
                    ).show();

                } catch (ApiException e) {

                    Toast.makeText(
                            MainActivity.this,
                            "Google sign-in failed. Code: "
                                    + e.getStatusCode(),
                            Toast.LENGTH_LONG
                    ).show();
                }

            } else {

                Toast.makeText(
                        MainActivity.this,
                        "Google sign-in cancelled.",
                        Toast.LENGTH_SHORT
                ).show();
            }

            return;
        }

        // --------------------------------------------------------
        // Google permission recovery
        // --------------------------------------------------------

        if (requestCode ==
                GOOGLE_RECOVERABLE_REQ) {

            if (resultCode == RESULT_OK) {

                googleAccount =
                        GoogleSignIn.getLastSignedInAccount(
                                this
                        );

                if (googleAccount != null) {
                    notifyWebViewGoogleAccount();
                }

                Toast.makeText(
                        MainActivity.this,
                        "Google Drive permission granted. Please try the backup or restore again.",
                        Toast.LENGTH_LONG
                ).show();

            } else {

                Toast.makeText(
                        MainActivity.this,
                        "Google Drive permission was not granted.",
                        Toast.LENGTH_LONG
                ).show();
            }

            return;
        }

        // --------------------------------------------------------
        // WebView file chooser
        // --------------------------------------------------------

        if (requestCode ==
                FILE_REQ) {

            if (fileChooserCallback != null) {

                Uri[] results = null;

                if (resultCode ==
                        RESULT_OK &&
                        data != null &&
                        data.getData() != null) {

                    results =
                            new Uri[]{
                                    data.getData()
                            };
                }

                fileChooserCallback
                        .onReceiveValue(results);

                fileChooserCallback = null;
            }

            return;
        }

        // --------------------------------------------------------
        // ARK JSON / Excel imports
        // --------------------------------------------------------

        if ((requestCode == 2101 ||
                requestCode == 2102) &&
                resultCode == RESULT_OK &&
                data != null &&
                data.getData() != null) {

            deliverPickedFile(
                    data.getData(),
                    requestCode == 2101
                            ? "backup"
                            : "excel"
            );
        }
    }

    // ============================================================
    // BACK BUTTON
    // ============================================================

    @Override
    public void onBackPressed() {

        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
