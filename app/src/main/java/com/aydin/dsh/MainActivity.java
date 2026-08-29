package com.aydin.dsh;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private LinearLayout loadingLayout;
    private TextView loadingText;
    private static final int PERMISSION_REQ_CODE = 101;
    private static final String PREFS_NAME = "DSH_PREFS";
    private static final String KEY_SERVER_URL = "SERVER_URL";
    private static final String LOCAL_URL = "http://127.0.0.1:3000";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isLoaded = false;

    private final BroadcastReceiver engineReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            connectToLocalDashboard();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        loadingLayout = findViewById(R.id.loadingLayout);
        loadingText = findViewById(R.id.loadingText);

        setupWebView();
        checkAndRequestPermissions();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(engineReceiver, new IntentFilter("com.aydin.dsh.ENGINE_READY"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(engineReceiver, new IntentFilter("com.aydin.dsh.ENGINE_READY"));
        }

        // Start On-Device DSH Server
        Intent serviceIntent = new Intent(this, LocalEngineService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        pollServerStatus();
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!url.equals("about:blank")) {
                    isLoaded = true;
                    loadingLayout.setVisibility(View.GONE);
                    webView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                return super.onConsoleMessage(consoleMessage);
            }
        });
    }

    private void pollServerStatus() {
        new Thread(() -> {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String targetUrl = prefs.getString(KEY_SERVER_URL, LOCAL_URL);

            for (int i = 0; i < 45; i++) {
                if (isLoaded) return;
                try {
                    URL url = new URL(targetUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(1000);
                    conn.setReadTimeout(1000);
                    if (conn.getResponseCode() == 200) {
                        handler.post(() -> {
                            if (!isLoaded) {
                                webView.loadUrl(targetUrl);
                            }
                        });
                        return;
                    }
                } catch (Exception ignored) {
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }

            handler.post(() -> {
                if (!isLoaded) {
                    loadingText.setText("Server starting taking longer than expected...");
                    showServerConfigDialog("Belum dapat terhubung ke server lokal. Anda juga bisa menghubungkan ke Remote VPS:");
                }
            });
        }).start();
    }

    private void connectToLocalDashboard() {
        if (!isLoaded) {
            handler.post(() -> webView.loadUrl(LOCAL_URL));
        }
    }

    private void showServerConfigDialog(String errorMessage) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String currentUrl = prefs.getString(KEY_SERVER_URL, LOCAL_URL);

        final EditText input = new EditText(this);
        input.setText(currentUrl);
        input.setHint("http://127.0.0.1:3000 atau http://IP-VPS:3000");
        input.setSingleLine(true);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Pengaturan Node DSH")
                .setMessage((errorMessage != null ? errorMessage + "\n\n" : "") +
                        "Pilih mode server DeepSeek Harness:")
                .setView(input)
                .setCancelable(true)
                .setPositiveButton("Hubungkan", (dialog, which) -> {
                    String url = input.getText().toString().trim();
                    if (!url.isEmpty()) {
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            url = "http://" + url;
                        }
                        prefs.edit().putString(KEY_SERVER_URL, url).apply();
                        webView.loadUrl(url);
                    }
                })
                .setNegativeButton("Reset ke Lokal HP", (dialog, which) -> {
                    prefs.edit().putString(KEY_SERVER_URL, LOCAL_URL).apply();
                    webView.loadUrl(LOCAL_URL);
                });

        builder.show();
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQ_CODE);
        }
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void openSettings() {
            runOnUiThread(() -> showServerConfigDialog(null));
        }

        @JavascriptInterface
        public void requestAllPermissions() {
            runOnUiThread(() -> checkAndRequestPermissions());
        }

        @JavascriptInterface
        public void showToast(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(engineReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("DSH Mobile")
                    .setMessage("Ganti mode server atau keluar?")
                    .setPositiveButton("Ganti Server", (d, w) -> showServerConfigDialog(null))
                    .setNegativeButton("Keluar", (d, w) -> finish())
                    .setNeutralButton("Batal", null)
                    .show();
        }
    }
}
