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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private LinearLayout loadingLayout;
    private LinearLayout controlBar;
    private TextView loadingText;
    private TextView subText;
    private TextView debugLogs;
    private TextView tvZoomLevel;
    private ScrollView logScrollView;
    private Button btnCopyLog;
    private Button btnRetry;
    private Button btnCloseLogs;
    private Button btnConfig;
    private Button btnToggleBar;
    private Button btnZoomIn;
    private Button btnZoomOut;
    private Button btnRefreshWeb;
    private Button btnDesktopMode;
    private Button btnPlugins;
    private Button btnLogs;

    private static final int PERMISSION_REQ_CODE = 101;
    private static final String PREFS_NAME = "DSH_PREFS";
    private static final String KEY_SERVER_URL = "SERVER_URL";
    private static final String KEY_ZOOM_LEVEL = "ZOOM_LEVEL";
    private static final String KEY_DESKTOP_MODE = "DESKTOP_MODE";
    private static final String KEY_BAR_VISIBLE = "BAR_VISIBLE";
    private static final String LOCAL_URL = "http://127.0.0.1:3080";
    private static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isLoaded = false;
    private int currentZoom = 70;
    private boolean isDesktopMode = true;
    private boolean isBarVisible = true;
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
        controlBar = findViewById(R.id.controlBar);
        loadingText = findViewById(R.id.loadingText);
        subText = findViewById(R.id.subText);
        debugLogs = findViewById(R.id.debugLogs);
        tvZoomLevel = findViewById(R.id.tvZoomLevel);
        logScrollView = findViewById(R.id.logScrollView);
        btnCopyLog = findViewById(R.id.btnCopyLog);
        btnRetry = findViewById(R.id.btnRetry);
        btnCloseLogs = findViewById(R.id.btnCloseLogs);
        btnConfig = findViewById(R.id.btnConfig);
        btnToggleBar = findViewById(R.id.btnToggleBar);
        btnZoomIn = findViewById(R.id.btnZoomIn);
        btnZoomOut = findViewById(R.id.btnZoomOut);
        btnRefreshWeb = findViewById(R.id.btnRefreshWeb);
        btnDesktopMode = findViewById(R.id.btnDesktopMode);
        btnPlugins = findViewById(R.id.btnPlugins);
        btnLogs = findViewById(R.id.btnLogs);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        currentZoom = prefs.getInt(KEY_ZOOM_LEVEL, 70);
        isDesktopMode = prefs.getBoolean(KEY_DESKTOP_MODE, true);
        isBarVisible = prefs.getBoolean(KEY_BAR_VISIBLE, true);

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

        btnCloseLogs.setOnClickListener(v -> {
            loadingLayout.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            btnToggleBar.setVisibility(View.VISIBLE);
            controlBar.setVisibility(isBarVisible ? View.VISIBLE : View.GONE);
        });

        btnLogs.setOnClickListener(v -> {
            loadingLayout.setVisibility(View.VISIBLE);
            btnCloseLogs.setVisibility(View.VISIBLE);
        });

        btnConfig.setOnClickListener(v -> showServerConfigDialog(null));

        btnToggleBar.setOnClickListener(v -> toggleControlBar());

        btnZoomIn.setOnClickListener(v -> adjustZoom(10));
        btnZoomOut.setOnClickListener(v -> adjustZoom(-10));

        btnRefreshWeb.setOnClickListener(v -> {
            Toast.makeText(this, "Memuat ulang dashboard...", Toast.LENGTH_SHORT).show();
            webView.reload();
        });

        btnDesktopMode.setOnClickListener(v -> toggleDesktopMode());

        btnPlugins.setOnClickListener(v -> showPluginManagerDialog());

        updateZoomDisplay();
    }

    private void toggleControlBar() {
        isBarVisible = !isBarVisible;
        controlBar.setVisibility(isBarVisible ? View.VISIBLE : View.GONE);
        btnToggleBar.setText(isBarVisible ? "✕" : "⚙️");
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_BAR_VISIBLE, isBarVisible).apply();
    }

    private void adjustZoom(int delta) {
        currentZoom = Math.max(30, Math.min(150, currentZoom + delta));
        applyZoom();
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_ZOOM_LEVEL, currentZoom).apply();
    }

    private void applyZoom() {
        double scaleFactor = currentZoom / 100.0;
        String js = "(() => {" +
                "  let meta = document.querySelector('meta[name=\"viewport\"]');" +
                "  if (!meta) {" +
                "    meta = document.createElement('meta');" +
                "    meta.name = 'viewport';" +
                "    document.head.appendChild(meta);" +
                "  }" +
                "  meta.content = 'width=1280, initial-scale=" + scaleFactor + ", minimum-scale=0.1, maximum-scale=3.0, user-scalable=yes';" +
                "  document.body.style.zoom = '" + scaleFactor + "';" +
                "  document.documentElement.style.zoom = '" + scaleFactor + "';" +
                "})();";
        webView.evaluateJavascript(js, null);
        updateZoomDisplay();
    }

    private void updateZoomDisplay() {
        tvZoomLevel.setText(currentZoom + "%");
    }

    private void toggleDesktopMode() {
        isDesktopMode = !isDesktopMode;
        applyModeSettings();
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_DESKTOP_MODE, isDesktopMode).apply();
        webView.reload();
    }

    private void applyModeSettings() {
        WebSettings settings = webView.getSettings();
        if (isDesktopMode) {
            settings.setUserAgentString(DESKTOP_UA);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            btnDesktopMode.setText("🖥️ Desk");
            btnDesktopMode.setTextColor(0xFF58A6FF);
        } else {
            settings.setUserAgentString(null);
            settings.setUseWideViewPort(false);
            settings.setLoadWithOverviewMode(false);
            btnDesktopMode.setText("📱 Mobile");
            btnDesktopMode.setTextColor(0xFF8B949E);
        }
    }

    private void showPluginManagerDialog() {
        final EditText input = new EditText(this);
        input.setHint("contoh: github:ben7am1n/dsh-telegram");
        input.setSingleLine(true);

        new AlertDialog.Builder(this)
                .setTitle("🔌 DSH Plugin Manager")
                .setMessage("Tambahkan Plugin Cordis / DeepSeek Harness:\n(Aplikasi akan otomatis mengunduh & restart engine)")
                .setView(input)
                .setPositiveButton("Pasang Plugin", (dialog, which) -> {
                    String pluginName = input.getText().toString().trim();
                    if (!pluginName.isEmpty()) {
                        installPlugin(pluginName);
                    }
                })
                .setNeutralButton("Lihat Daftar Plugin", (dialog, which) -> listInstalledPlugins())
                .setNegativeButton("Batal", null)
                .show();
    }

    private void installPlugin(String pluginName) {
        appendLog("\n>>> [PLUGIN] Menginstall plugin: " + pluginName + "...");
        Toast.makeText(this, "Sedang memasang plugin: " + pluginName, Toast.LENGTH_LONG).show();

        new Thread(() -> {
            try {
                File filesDir = getFilesDir();
                File nodeFile = new File(filesDir, "bin/node");
                File libDir = new File(filesDir, "lib");
                File dshDir = new File(filesDir, "dsh");
                File dshBin = new File(dshDir, "lib/bin.js");

                ProcessBuilder pb = new ProcessBuilder(
                        nodeFile.getAbsolutePath(),
                        dshBin.getAbsolutePath(),
                        "plugin",
                        "--profile", "web",
                        "add",
                        pluginName
                );
                pb.directory(filesDir);
                pb.environment().put("HOME", filesDir.getAbsolutePath());
                pb.environment().put("LD_LIBRARY_PATH", libDir.getAbsolutePath());
                pb.environment().put("NODE_PATH", new File(dshDir, "node_modules").getAbsolutePath());
                pb.environment().put("PATH", new File(filesDir, "bin").getAbsolutePath() + ":/system/bin");
                pb.redirectErrorStream(true);

                Process proc = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        appendLog("[PLUGIN LOG] " + line);
                    }
                }
                int exitCode = proc.waitFor();
                appendLog("[PLUGIN] Selesai dengan kode: " + exitCode);

                handler.post(() -> {
                    Toast.makeText(this, "Plugin selesai dipasang! Me-restart server...", Toast.LENGTH_SHORT).show();
                    stopService(new Intent(this, LocalEngineService.class));
                    startEngineService();
                });
            } catch (Exception e) {
                appendLog("[PLUGIN ERROR] " + e.getMessage());
            }
        }).start();
    }

    private void listInstalledPlugins() {
        File filesDir = getFilesDir();
        File pluginsDir = new File(filesDir, ".dsh/profiles/web/cordis.yml");
        String info = pluginsDir.exists() ? "File konfigurasi aktif: .dsh/profiles/web/cordis.yml" : "Belum ada plugin eksternal terpasang.";
        new AlertDialog.Builder(this)
                .setTitle("Status Plugin Web Profile")
                .setMessage(info)
                .setPositiveButton("Tutup", null)
                .show();
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
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        applyModeSettings();

        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!url.equals("about:blank")) {
                    isLoaded = true;
                    loadingLayout.setVisibility(View.GONE);
                    webView.setVisibility(View.VISIBLE);
                    btnToggleBar.setVisibility(View.VISIBLE);
                    controlBar.setVisibility(isBarVisible ? View.VISIBLE : View.GONE);
                    btnToggleBar.setText(isBarVisible ? "✕" : "⚙️");
                    applyZoom();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                String errDesc = error.getDescription().toString();
                appendLog("[WebView Error] " + errDesc + " on " + request.getUrl());

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
        input.setHint("http://127.0.0.1:3080 atau http://IP-VPS:3080");
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
                .setNegativeButton("Lokal HP (127.0.0.1:3080)", (dialog, which) -> {
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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
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

    public static class WebAppInterface {
        @JavascriptInterface
        public String getPlatform() {
            return "android";
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(engineReceiver);
        super.onDestroy();
    }
}
