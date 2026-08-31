package com.dsh.mobile;

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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
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
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
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
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private LinearLayout loadingLayout;
    private LinearLayout floatingDraggableContainer;
    private HorizontalScrollView controlBarScroll;
    private TextView loadingText;
    private TextView subText;
    private TextView debugLogs;
    private TextView tvZoomLevel;
    private ScrollView logScrollView;
    private Button btnCopyLog;
    private Button btnRetry;
    private Button btnCloseLogs;
    private Button btnConfig;
    private ImageButton btnToggleBar;
    private Button btnCloseBar;
    private Button btnZoomIn;
    private Button btnZoomOut;
    private Button btnRefreshWeb;
    private Button btnRootToggle;
    private Button btnDesktopMode;
    private Button btnPlugins;
    private Button btnLogs;
    private Button btnConfigDoc;

    private static final int PERMISSION_REQ_CODE = 101;
    private static final int MANAGE_STORAGE_REQ_CODE = 102;
    private static final String PREFS_NAME = "DSH_PREFS";
    private static final String KEY_SERVER_URL = "SERVER_URL";
    private static final String KEY_ZOOM_LEVEL = "ZOOM_LEVEL";
    private static final String KEY_DESKTOP_MODE = "DESKTOP_MODE";
    private static final String KEY_BAR_VISIBLE = "BAR_VISIBLE";
    private static final String KEY_ROOT_ENABLED = "ROOT_ENABLED";
    private static final String LOCAL_URL = "http://127.0.0.1:3080";
    private static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isLoaded = false;
    private boolean engineServiceStarted = false;
    private int currentZoom = 100;
    private boolean isDesktopMode = false;
    private boolean isBarVisible = false;
    private final StringBuilder logAccumulator = new StringBuilder();

    // Drag variables for moving the floating toolbar
    private float startX, startY;
    private float dX, dY;
    private boolean isDragging = false;
    private int touchSlop;

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

        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        webView = findViewById(R.id.webView);
        loadingLayout = findViewById(R.id.loadingLayout);
        floatingDraggableContainer = findViewById(R.id.floatingDraggableContainer);
        controlBarScroll = findViewById(R.id.controlBarScroll);
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
        btnCloseBar = findViewById(R.id.btnCloseBar);
        btnZoomIn = findViewById(R.id.btnZoomIn);
        btnZoomOut = findViewById(R.id.btnZoomOut);
        btnRefreshWeb = findViewById(R.id.btnRefreshWeb);
        btnRootToggle = findViewById(R.id.btnRootToggle);
        btnDesktopMode = findViewById(R.id.btnDesktopMode);
        btnPlugins = findViewById(R.id.btnPlugins);
        btnLogs = findViewById(R.id.btnLogs);
        btnConfigDoc = findViewById(R.id.btnConfigDoc);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        currentZoom = prefs.getInt(KEY_ZOOM_LEVEL, 100);
        isDesktopMode = prefs.getBoolean(KEY_DESKTOP_MODE, false);
        isBarVisible = prefs.getBoolean(KEY_BAR_VISIBLE, false);
        boolean isRootPersisted = prefs.getBoolean(KEY_ROOT_ENABLED, false);

        if (isRootPersisted) {
            btnRootToggle.setText("👑 Root OK");
            btnRootToggle.setTextColor(0xFF3FB950);
            File flagFile = new File(getFilesDir(), "root_enabled.flag");
            try { flagFile.createNewFile(); } catch (Exception ignored) {}
        }

        setupButtons();
        setupDraggableFloatingBar();
        setupWebView();

        IntentFilter filter = new IntentFilter();
        filter.addAction(LocalEngineService.ACTION_LOG);
        filter.addAction(LocalEngineService.ACTION_READY);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(engineReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(engineReceiver, filter);
        }

        checkAndRequestPermissions();
    }

    private void setupDraggableFloatingBar() {
        btnToggleBar.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getRawX();
                    startY = event.getRawY();
                    dX = floatingDraggableContainer.getX() - startX;
                    dY = floatingDraggableContainer.getY() - startY;
                    isDragging = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float moveX = Math.abs(event.getRawX() - startX);
                    float moveY = Math.abs(event.getRawY() - startY);
                    if (moveX > touchSlop || moveY > touchSlop) {
                        isDragging = true;
                        floatingDraggableContainer.setX(event.getRawX() + dX);
                        floatingDraggableContainer.setY(event.getRawY() + dY);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!isDragging) {
                        toggleControlBar();
                    }
                    return true;

                default:
                    return false;
            }
        });
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
            floatingDraggableContainer.setVisibility(View.VISIBLE);
            controlBarScroll.setVisibility(isBarVisible ? View.VISIBLE : View.GONE);
        });

        btnLogs.setOnClickListener(v -> {
            loadingLayout.setVisibility(View.VISIBLE);
            btnCloseLogs.setVisibility(View.VISIBLE);
        });

        btnCloseBar.setOnClickListener(v -> {
            isBarVisible = false;
            controlBarScroll.setVisibility(View.GONE);
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_BAR_VISIBLE, false).apply();
        });

        btnConfig.setOnClickListener(v -> showServerConfigDialog(null));
        btnConfigDoc.setOnClickListener(v -> showConfigFileViewerDialog(null));

        btnZoomIn.setOnClickListener(v -> adjustZoom(10));
        btnZoomOut.setOnClickListener(v -> adjustZoom(-10));

        btnRefreshWeb.setOnClickListener(v -> {
            Toast.makeText(this, "Memuat ulang dashboard...", Toast.LENGTH_SHORT).show();
            webView.reload();
        });

        btnRootToggle.setOnClickListener(v -> requestRootSuperuserAccess());

        btnDesktopMode.setOnClickListener(v -> toggleDesktopMode());

        btnPlugins.setOnClickListener(v -> showPluginManagerDialog());

        updateZoomDisplay();
    }

    private void requestRootSuperuserAccess() {
        Toast.makeText(this, "Memeriksa & meminta izin Root (SU)...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean granted = false;
            String output = "";
            String[] suCheckCommands = new String[]{
                "/product/bin/magisk",
                "/system/bin/magisk",
                "/system/xbin/magisk",
                "/data/adb/magisk/magisk",
                "/data/adb/ksu/bin/su",
                "/data/adb/ksud",
                "/data/adb/ap/bin/su",
                "/data/adb/magisk/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/vendor/bin/su",
                "/sbin/su",
                "su"
            };

            for (String suBin : suCheckCommands) {
                try {
                    Process p = Runtime.getRuntime().exec(new String[]{suBin, "-c", "id"});
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                        String line = reader.readLine();
                        if (line != null && line.contains("uid=0")) {
                            granted = true;
                            output = line + " (via " + suBin + ")";
                            p.waitFor();
                            break;
                        }
                    }
                    p.waitFor();
                } catch (Exception e) {
                    output = e.getMessage();
                }
            }

            final boolean isRooted = granted;
            final String details = output;
            
            // Persist root state in SharedPreferences and create/delete root_enabled.flag
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_ROOT_ENABLED, isRooted).apply();
            File flagFile = new File(getFilesDir(), "root_enabled.flag");
            if (isRooted) {
                try { flagFile.createNewFile(); } catch (Exception ignored) {}
            } else {
                if (flagFile.exists()) flagFile.delete();
            }

            handler.post(() -> {
                if (isRooted) {
                    btnRootToggle.setText("👑 Root OK");
                    btnRootToggle.setTextColor(0xFF3FB950);
                    appendLog("\n>>> [ROOT STATUS] Root Superuser (uid=0) Aktif: " + details);
                    new AlertDialog.Builder(this)
                            .setTitle("👑 Root Superuser Aktif")
                            .setMessage("Izin Root (SU) berhasil didapatkan!\nAgent DeepSeek Harness sekarang memiliki akses Superuser penuh (uid=0) langsung ke sistem Android.\n\nDetail: " + details)
                            .setPositiveButton("Bagus", null)
                            .show();
                } else {
                    btnRootToggle.setText("👑 Request SU");
                    btnRootToggle.setTextColor(0xFFD2A8FF);
                    appendLog("\n>>> [ROOT STATUS] Root SU tidak tersedia atau ditolak: " + details);
                    new AlertDialog.Builder(this)
                            .setTitle("Status Root Superuser")
                            .setMessage("Perangkat belum di-root atau izin SU ditolak di Magisk / KernelSU / APatch.\n\nCatatan: Jika ditolak, aplikasi akan berjalan menggunakan sandbox PRoot.")
                            .setPositiveButton("Mengerti", null)
                            .show();
                }
            });
        }).start();
    }

    private void toggleControlBar() {
        isBarVisible = !isBarVisible;
        controlBarScroll.setVisibility(isBarVisible ? View.VISIBLE : View.GONE);
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_BAR_VISIBLE, isBarVisible).apply();
    }

    private void adjustZoom(int delta) {
        currentZoom = Math.max(40, Math.min(200, currentZoom + delta));
        applyWholeElementZoom();
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_ZOOM_LEVEL, currentZoom).apply();
    }

    private void applyWholeElementZoom() {
        webView.setInitialScale(currentZoom);
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
        if (engineServiceStarted) return;
        engineServiceStarted = true;

        appendLog("[INIT] Memulai Engine Service setelah perizinan storage siap...");
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

        // Keep text zoom neutral (100%)
        settings.setTextZoom(100);

        applyModeSettings();
        applyWholeElementZoom();

        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!url.equals("about:blank")) {
                    isLoaded = true;
                    loadingLayout.setVisibility(View.GONE);
                    webView.setVisibility(View.VISIBLE);
                    floatingDraggableContainer.setVisibility(View.VISIBLE);
                    controlBarScroll.setVisibility(isBarVisible ? View.VISIBLE : View.GONE);
                    applyWholeElementZoom();
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
                    startActivityForResult(intent, MANAGE_STORAGE_REQ_CODE);
                    return;
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, MANAGE_STORAGE_REQ_CODE);
                    return;
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
        } else {
            startEngineService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_CODE) {
            startEngineService();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If coming back from Settings (Manage All Files Permission), start engine safely
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            if (!engineServiceStarted) {
                startEngineService();
            }
        }
    }

    public class WebAppInterface {
        @JavascriptInterface
        public String getPlatform() {
            return "android";
        }

        @JavascriptInterface
        public void openFileViewer(String path) {
            handler.post(() -> showConfigFileViewerDialog(path));
        }
    }

    private void showConfigFileViewerDialog(String filePath) {
        try {
            File file = (filePath != null && !filePath.isEmpty()) ? new File(filePath) : new File(getFilesDir(), ".dsh/profiles/web/cordis.yml");
            if (!file.exists()) {
                File alt = new File(getFilesDir(), ".dsh/config.yml");
                if (alt.exists()) file = alt;
            }

            StringBuilder content = new StringBuilder();
            if (file.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    String l;
                    while ((l = br.readLine()) != null) {
                        content.append(l).append("\n");
                    }
                }
            } else {
                content.append("# DeepSeek Harness Configuration\n# File: ").append(file.getAbsolutePath()).append("\n\n");
            }

            final File targetFile = file;
            final EditText editor = new EditText(this);
            editor.setText(content.toString());
            editor.setTypeface(android.graphics.Typeface.MONOSPACE);
            editor.setTextSize(12);
            editor.setHorizontallyScrolling(true);

            ScrollView scroll = new ScrollView(this);
            scroll.addView(editor);

            new AlertDialog.Builder(this)
                    .setTitle("⚙️ File Konfigurasi (" + targetFile.getName() + ")")
                    .setView(scroll)
                    .setPositiveButton("Simpan & Restart", (dialog, which) -> {
                        try (FileWriter fw = new FileWriter(targetFile)) {
                            fw.write(editor.getText().toString());
                            Toast.makeText(this, "Konfigurasi disimpan! Merestart server...", Toast.LENGTH_SHORT).show();
                            stopService(new Intent(this, LocalEngineService.class));
                            startEngineService();
                        } catch (Exception e) {
                            Toast.makeText(this, "Gagal menyimpan: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNeutralButton("Buka di Editor Eksternal", (dialog, which) -> {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(Uri.fromFile(targetFile), "text/*");
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(Intent.createChooser(intent, "Buka dengan..."));
                        } catch (Exception e) {
                            Toast.makeText(this, "Tidak ada aplikasi editor teks: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Tutup", null)
                    .show();
        } catch (Exception err) {
            Toast.makeText(this, "Error: " + err.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(engineReceiver);
        super.onDestroy();
    }
}
