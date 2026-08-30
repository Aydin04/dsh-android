package com.aydin.dsh;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
    private TextView subText;
    private TextView debugLogs;
    private ScrollView logScrollView;
    private Button btnCopyLog;
    private Button btnRetry;
    private Button btnConfig;

    private static final int PERMISSION_REQ_CODE = 101;
    private static final String PREFS_NAME = "DSH_PREFS";
    private static final String KEY_SERVER_URL = "SERVER_URL";
    private static final String LOCAL_URL = "http://127.0.0.1:3000";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isLoaded = false;
    private final StringBuilder logAccumulator = new StringBuilder();

    private final BroadcastReceiver engineReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (LocalEngineService.ACTION_LOG.equals(action)) {
                String msg = intent.getStringExtra(LocalEngineService.EXTRA_MESSAGE);
                appendLog(msg);
            } else if (LocalEngineService.ACTION_READY.equals(action)) {
                appendLog(">>> [EVENT] Engine reported READY. Connecting WebView...");
                connectToLocalDashboard();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        loadingLayout = findViewById(R.id.loadingLayout);
        loadingText = findViewById(R.id.loadingText);
        subText = findViewById(R.id.subText);
        debugLogs = findViewById(R.id.debugLogs);
        logScrollView = findViewById(R.id.logScrollView);
        btnCopyLog = findViewById(R.id.btnCopyLog);
        btnRetry = findViewById(R.id.btnRetry);
        btnConfig = findViewById(R.id.btnConfig);

        setupButtons();
        setupWebView();
        checkAndRequestPermissions();

        IntentFilter filter = new IntentFilter();
        filter.addAction(LocalEngineService.ACTION_LOG);
        filter.addAction(LocalEngineService.ACTION_READY);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(engineReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(engineReceiver, filter);
        }

        startEngineService();
    }

    private void setupButtons() {
        btnCopyLog.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData cd = ClipData.newPlainText("DSH Engine Logs", logAccumulator.toString());
            cm.setPrimaryClip(cd);
            Toast.makeText(this, "Logs copied to clipboard!", Toast.LENGTH_SHORT).show();
        });

        btnRetry.setOnClickListener(v -> {
            appendLog("\n=== [RESTART] Restarting Engine Service ===");
            stopService(new Intent(this, LocalEngineService.class));
            startEngineService();
        });

        btnConfig.setOnClickListener(v -> showServerConfigDialog(null));
    }

    private void startEngineService() {
        Intent serviceIntent = new Intent(this, LocalEngineService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void appendLog(String message) {
        handler.post(() -> {
            logAccumulator.append(message).append("\n");
            debugLogs.setText(logAccumulator.toString());
            logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
        });
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
                String errDesc = error.getDescription().toString();
                appendLog("[WebView Error] " + errDesc + " on " + request.getUrl());
                
                // Auto-retry reload if connection was reset or refused during initial server warm-up
                if (request.isForMainFrame() && (errDesc.contains("ERR_CONNECTION_RESET") || errDesc.contains("ERR_CONNECTION_REFUSED") || errDesc.contains("net::ERR_"))) {
                    handler.postDelayed(() -> {
                        appendLog("[WebView Retry] Retrying connection to dashboard...");
                        view.loadUrl(LOCAL_URL);
                    }, 1500);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                appendLog("[WebConsole] " + consoleMessage.message());
                return super.onConsoleMessage(consoleMessage);
            }
        });
    }

    private void connectToLocalDashboard() {
        handler.post(() -> {
            if (!isLoaded) {
                webView.loadUrl(LOCAL_URL);
            }
        });
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
                .setNegativeButton("Lokal HP (127.0.0.1:3000)", (dialog, which) -> {
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
        if (webView.canGoBack() && webView.getVisibility() == View.VISIBLE) {
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
