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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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

    private ProgressBar progressBarPercent;
    private TextView tvProgressPercent;
    private TextView tvCurrentLogLine;
    private Button btnShowAllLogs;
    private LinearLayout logActionsLayout;
    private Button btnNode;

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
    private int simulatedProgress = 10;

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
                updateProgress(100, "Dashboard Siap! Menghubungkan...");
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
        btnNode = findViewById(R.id.btnNode);

        progressBarPercent = findViewById(R.id.progressBarPercent);
        tvProgressPercent = findViewById(R.id.tvProgressPercent);
        tvCurrentLogLine = findViewById(R.id.tvCurrentLogLine);
        btnShowAllLogs = findViewById(R.id.btnShowAllLogs);
        logActionsLayout = findViewById(R.id.logActionsLayout);

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

    private void updateProgress(int targetPercent, String status) {
        handler.post(() -> {
            simulatedProgress = Math.max(simulatedProgress, targetPercent);
            if (progressBarPercent != null) progressBarPercent.setProgress(simulatedProgress);
            if (tvProgressPercent != null) tvProgressPercent.setText(simulatedProgress + "%");
            if (tvCurrentLogLine != null && status != null) tvCurrentLogLine.setText(status);
        });
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

        btnRetry.setOnClickListener(v -> restartEngine());

        btnCloseLogs.setOnClickListener(v -> {
            if (isLoaded) {
                loadingLayout.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                floatingDraggableContainer.setVisibility(View.VISIBLE);
                controlBarScroll.setVisibility(isBarVisible ? View.VISIBLE : View.GONE);
            } else {
                logScrollView.setVisibility(View.GONE);
                logActionsLayout.setVisibility(View.GONE);
            }
        });

        btnShowAllLogs.setOnClickListener(v -> {
            boolean isVisible = logScrollView.getVisibility() == View.VISIBLE;
            logScrollView.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            logActionsLayout.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            btnShowAllLogs.setText(isVisible ? "📋 Detail Log" : "Sembunyikan Log");
        });

        btnLogs.setOnClickListener(v -> {
            loadingLayout.setVisibility(View.VISIBLE);
            logScrollView.setVisibility(View.VISIBLE);
            logActionsLayout.setVisibility(View.VISIBLE);
            btnCloseLogs.setVisibility(View.VISIBLE);
        });

        btnCloseBar.setOnClickListener(v -> {
            isBarVisible = false;
            controlBarScroll.setVisibility(View.GONE);
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_BAR_VISIBLE, false).apply();
        });

        btnConfig.setOnClickListener(v -> showServerConfigDialog(null));
        if (btnNode != null) {
            btnNode.setOnClickListener(v -> showServerConfigDialog(null));
        }
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
        input.setHint("contoh: dsh-defend atau github:user/repo");
        input.setSingleLine(true);

        new AlertDialog.Builder(this)
                .setTitle("🔌 DSH Plugin Manager")
                .setMessage("Masukkan nama package plugin yang ingin dipasang:\n(Contoh: 'dsh-defend' atau 'github:user/repo')\n\nCatatan: Engine akan otomatis menginstal ke profil web dan me-restart server.")
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

    private void installPlugin(String rawInput) {
        String pluginName = rawInput.trim();
        if (pluginName.contains(" ")) {
            String[] parts = pluginName.split("\\s+");
            pluginName = parts[parts.length - 1]; // take the last argument
        }

        // Support direct GitHub URLs: https://github.com/PerryLink/dsh-defend -> github:PerryLink/dsh-defend
        if (pluginName.startsWith("https://github.com/") || pluginName.startsWith("http://github.com/")) {
            pluginName = pluginName.replaceFirst("https?://github.com/", "github:");
            if (pluginName.endsWith(".git")) {
                pluginName = pluginName.substring(0, pluginName.length() - 4);
            }
        }

        final String targetPlugin = pluginName;

        // Create live progress dialog for plugin installation
        final TextView logView = new TextView(this);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setTextSize(11);
        logView.setTextColor(0xFF3FB950);
        logView.setBackgroundColor(0xFF0D1117);
        logView.setPadding(24, 24, 24, 24);
        logView.setText("[PLUGIN] Memulai instalasi " + targetPlugin + "...\n");

        final ScrollView logScroll = new ScrollView(this);
        logScroll.addView(logView);

        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle("📦 Menginstall Plugin (" + targetPlugin + ")")
                .setView(logScroll)
                .setCancelable(false)
                .setPositiveButton("Tutup & Restart Engine", null)
                .create();
        progressDialog.show();
        progressDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

        new Thread(() -> {
            try {
                File filesDir = getFilesDir();
                File nodeFile = new File(filesDir, "bin/node");
                File libDir = new File(filesDir, "lib");
                File dshDir = new File(filesDir, "dsh");
                File dshBin = new File(dshDir, "lib/bin.js");

                // Ensure .dsh/profiles/web directory exists
                File webProfileDir = new File(filesDir, ".dsh/profiles/web");
                if (!webProfileDir.exists()) {
                    webProfileDir.mkdirs();
                }

                String nodePath = new File(dshDir, "node_modules").getAbsolutePath() + 
                        ":" + new File(filesDir, ".dsh/profiles/web/node_modules").getAbsolutePath() +
                        ":" + new File(filesDir, "node_modules").getAbsolutePath();

                String pnpmBinPath = new File(dshDir, "node_modules/pnpm/bin/pnpm.cjs").getAbsolutePath();
                File pnpmBinFile = new File(pnpmBinPath);
                
                ProcessBuilder pb;
                if (!pnpmBinFile.exists()) {
                    // Fallback to npm if pnpm is not extracted
                    handler.post(() -> {
                        logView.append("[WARN] pnpm tidak ditemukan, mencoba npm fallback...\n");
                        logScroll.fullScroll(View.FOCUS_DOWN);
                    });
                    pb = new ProcessBuilder(
                            nodeFile.getAbsolutePath(),
                            dshBin.getAbsolutePath(),
                            "plugin",
                            "--profile", "web",
                            "add",
                            targetPlugin
                    );
                } else {
                    pb = new ProcessBuilder(
                            nodeFile.getAbsolutePath(),
                            dshBin.getAbsolutePath(),
                            "plugin",
                            "--profile", "web",
                            "add",
                            targetPlugin
                    );
                }

                pb.directory(filesDir);
                pb.environment().put("HOME", filesDir.getAbsolutePath());
                pb.environment().put("LD_LIBRARY_PATH", libDir.getAbsolutePath());
                pb.environment().put("NODE_PATH", nodePath);
                pb.environment().put("PATH", new File(filesDir, "bin").getAbsolutePath() + ":/system/bin:/data/data/com.termux/files/usr/bin");
                pb.redirectErrorStream(true);

                Process proc = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String outLine = line;
                        appendLog("[PLUGIN LOG] " + outLine);
                        handler.post(() -> {
                            logView.append(outLine + "\n");
                            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
                        });
                    }
                }
                int exitCode = proc.waitFor();
                appendLog("[PLUGIN] Selesai dengan kode: " + exitCode);

                handler.post(() -> {
                    if (exitCode == 0) {
                        logView.append("\n✅ [BERHASIL] Plugin " + targetPlugin + " sukses terpasang!\nSilakan klik tombol di bawah untuk restart engine.");
                    } else {
                        logView.append("\n❌ [GAGAL] Instalasi selesai dengan exit code: " + exitCode + ".\nPeriksa koneksi internet atau nama package.");
                    }
                    logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
                    Button btn = progressDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    btn.setEnabled(true);
                    btn.setText("Restart Engine Sekarang");
                    btn.setOnClickListener(v -> {
                        progressDialog.dismiss();
                        restartEngine();
                    });
                });
            } catch (Exception e) {
                appendLog("[PLUGIN ERROR] " + e.getMessage());
                handler.post(() -> {
                    logView.append("\n[FATAL ERROR] " + e.getMessage() + "\n");
                    Button btn = progressDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    btn.setEnabled(true);
                    btn.setText("Tutup");
                    btn.setOnClickListener(v -> progressDialog.dismiss());
                });
            }
        }).start();
    }

    private void removePlugin(String pluginName) {
        final String targetPlugin = pluginName.trim();
        final TextView logView = new TextView(this);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setTextSize(11);
        logView.setTextColor(0xFFF85149);
        logView.setBackgroundColor(0xFF0D1117);
        logView.setPadding(24, 24, 24, 24);
        logView.setText("[PLUGIN] Menghapus plugin " + targetPlugin + "...\n");

        final ScrollView logScroll = new ScrollView(this);
        logScroll.addView(logView);

        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle("🗑️ Menghapus Plugin (" + targetPlugin + ")")
                .setView(logScroll)
                .setCancelable(false)
                .setPositiveButton("Tutup & Restart Engine", null)
                .create();
        progressDialog.show();
        progressDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

        new Thread(() -> {
            try {
                File filesDir = getFilesDir();
                File nodeFile = new File(filesDir, "bin/node");
                File libDir = new File(filesDir, "lib");
                File dshDir = new File(filesDir, "dsh");
                File dshBin = new File(dshDir, "lib/bin.js");

                String nodePath = new File(dshDir, "node_modules").getAbsolutePath() + 
                        ":" + new File(filesDir, ".dsh/profiles/web/node_modules").getAbsolutePath() +
                        ":" + new File(filesDir, "node_modules").getAbsolutePath();

                ProcessBuilder pb = new ProcessBuilder(
                        nodeFile.getAbsolutePath(),
                        dshBin.getAbsolutePath(),
                        "plugin",
                        "--profile", "web",
                        "remove",
                        targetPlugin
                );
                pb.directory(filesDir);
                pb.environment().put("HOME", filesDir.getAbsolutePath());
                pb.environment().put("LD_LIBRARY_PATH", libDir.getAbsolutePath());
                pb.environment().put("NODE_PATH", nodePath);
                pb.environment().put("PATH", new File(filesDir, "bin").getAbsolutePath() + ":/system/bin:/data/data/com.termux/files/usr/bin");
                pb.redirectErrorStream(true);

                Process proc = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String outLine = line;
                        appendLog("[PLUGIN REMOVE] " + outLine);
                        handler.post(() -> {
                            logView.append(outLine + "\n");
                            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
                        });
                    }
                }
                int exitCode = proc.waitFor();
                appendLog("[PLUGIN REMOVE] Selesai dengan kode: " + exitCode);

                handler.post(() -> {
                    if (exitCode == 0) {
                        logView.append("\n✅ [BERHASIL] Plugin " + targetPlugin + " sukses dihapus dari profil web!\nSilakan klik tombol di bawah untuk restart engine.");
                    } else {
                        logView.append("\n⚠️ Proses hapus selesai dengan exit code: " + exitCode + ".");
                    }
                    logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
                    Button btn = progressDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    btn.setEnabled(true);
                    btn.setText("Restart Engine Sekarang");
                    btn.setOnClickListener(v -> {
                        progressDialog.dismiss();
                        restartEngine();
                    });
                });
            } catch (Exception e) {
                appendLog("[PLUGIN REMOVE ERROR] " + e.getMessage());
                handler.post(() -> {
                    logView.append("\n[FATAL ERROR] " + e.getMessage() + "\n");
                    Button btn = progressDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    btn.setEnabled(true);
                    btn.setText("Tutup");
                    btn.setOnClickListener(v -> progressDialog.dismiss());
                });
            }
        }).start();
    }

    private void listInstalledPlugins() {
        File filesDir = getFilesDir();
        File profilePackageJson = new File(filesDir, ".dsh/profiles/web/package.json");

        final java.util.List<String> installedPluginNames = new java.util.ArrayList<>();
        if (profilePackageJson.exists()) {
            try {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new FileReader(profilePackageJson))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }
                org.json.JSONObject obj = new org.json.JSONObject(sb.toString());
                if (obj.has("dependencies")) {
                    org.json.JSONObject deps = obj.getJSONObject("dependencies");
                    java.util.Iterator<String> keys = deps.keys();
                    while (keys.hasNext()) {
                        installedPluginNames.add(keys.next());
                    }
                }
            } catch (Exception ignored) {}
        }

        if (installedPluginNames.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("🔌 Daftar Plugin Web Profile")
                    .setMessage("Belum ada plugin tambahan yang terpasang di profile web.\n\nDefault Built-in Plugins aktif:\n• @deepseek-ai/dsh-base\n• @deepseek-ai/dsh-web-app\n• @deepseek-ai/dsh-host-plugin-inventory\n• @deepseek-ai/dsh-cordis-host-runner")
                    .setPositiveButton("Tutup", null)
                    .show();
            return;
        }

        String[] items = new String[installedPluginNames.size()];
        for (int i = 0; i < installedPluginNames.size(); i++) {
            items[i] = "📦 " + installedPluginNames.get(i);
        }

        new AlertDialog.Builder(this)
                .setTitle("🔌 Plugin Terpasang (" + installedPluginNames.size() + ")")
                .setItems(items, (dialog, which) -> {
                    String selectedPlugin = installedPluginNames.get(which);
                    new AlertDialog.Builder(this)
                            .setTitle("Kelola: " + selectedPlugin)
                            .setMessage("Pilih tindakan untuk plugin '" + selectedPlugin + "':")
                            .setPositiveButton("🗑️ Hapus Plugin", (d, w) -> removePlugin(selectedPlugin))
                            .setNegativeButton("Batal", null)
                            .show();
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    private void restartEngine() {
        appendLog("\n=== [RESTART] Restarting Engine Service ===");
        engineServiceStarted = false;
        isLoaded = false;
        stopService(new Intent(this, LocalEngineService.class));
        handler.postDelayed(this::startEngineService, 1000);
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

            // Clean log preview for single line status
            String cleanMsg = message.replaceAll("\\[[0-9;]*m", "").trim();
            if (!cleanMsg.isEmpty()) {
                if (cleanMsg.contains("[INIT]")) {
                    updateProgress(15, "Inisialisasi lingkungan perangkat...");
                } else if (cleanMsg.contains("Node.js binary")) {
                    updateProgress(30, "Mengekstrak binary Node.js ARM64...");
                } else if (cleanMsg.contains("Ripgrep")) {
                    updateProgress(40, "Menyiapkan Ripgrep search engine...");
                } else if (cleanMsg.contains("PRoot binary")) {
                    updateProgress(50, "Menyiapkan PRoot sandbox engine...");
                } else if (cleanMsg.contains("Alpine Linux")) {
                    updateProgress(65, "Mengekstrak Alpine Linux rootfs...");
                } else if (cleanMsg.contains("shared libraries")) {
                    updateProgress(75, "Menyiapkan native shared libraries...");
                } else if (cleanMsg.contains("DeepSeek Harness")) {
                    updateProgress(85, "Memuat modul core DeepSeek Harness...");
                } else if (cleanMsg.contains("Launching dsh")) {
                    updateProgress(92, "Menjalankan DeepSeek Harness server...");
                } else if (cleanMsg.contains("HTTP 200 OK") || cleanMsg.contains("[READY]")) {
                    updateProgress(100, "Dashboard Siap! Menghubungkan...");
                } else {
                    if (tvCurrentLogLine != null) {
                        tvCurrentLogLine.setText(cleanMsg);
                    }
                }
            }
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

                    // Inject custom script: Enter makes newline, submit only on Send button click
                    injectChatEnterKeyHandler(view);
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

    private void injectChatEnterKeyHandler(WebView view) {
        String js = "(function() {" +
                "  if (window.__dshEnterPatched) return;" +
                "  window.__dshEnterPatched = true;" +
                "  document.addEventListener('keydown', function(e) {" +
                "    if (e.key === 'Enter' && !e.shiftKey && !e.ctrlKey && !e.altKey && !e.metaKey) {" +
                "      var target = e.target;" +
                "      if (target && (target.tagName === 'TEXTAREA' || target.isContentEditable || target.getAttribute('role') === 'textbox')) {" +
                "        e.stopPropagation();" +
                "      }" +
                "    }" +
                "  }, true);" +
                "})();";
        view.evaluateJavascript(js, null);
    }

    private void showServerConfigDialog(String errorMessage) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String currentUrl = prefs.getString(KEY_SERVER_URL, LOCAL_URL);

        final EditText input = new EditText(this);
        input.setText(currentUrl);
        input.setHint("http://127.0.0.1:3080 atau http://IP_VPS:3080");
        input.setSingleLine(true);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("🌐 Pengaturan Node / Server DSH")
                .setMessage((errorMessage != null ? errorMessage + "\n\n" : "") +
                        "Pilih backend DeepSeek Harness yang ingin digunakan:\n\n" +
                        "• Lokal HP: http://127.0.0.1:3080\n" +
                        "• Remote VPS / Node Server: Masukkan URL server eksternal (misal: http://192.168.1.50:3080 atau http://vps-anda.com:3080)")
                .setView(input)
                .setCancelable(true)
                .setPositiveButton("Hubungkan", (dialog, which) -> {
                    String url = input.getText().toString().trim();
                    if (!url.isEmpty()) {
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            url = "http://" + url;
                        }
                        prefs.edit().putString(KEY_SERVER_URL, url).apply();
                        Toast.makeText(this, "Menghubungkan ke: " + url, Toast.LENGTH_SHORT).show();
                        webView.loadUrl(url);
                    }
                })
                .setNegativeButton("Lokal HP (Default)", (dialog, which) -> {
                    prefs.edit().putString(KEY_SERVER_URL, LOCAL_URL).apply();
                    Toast.makeText(this, "Beralih ke Engine Lokal HP", Toast.LENGTH_SHORT).show();
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
            File profileDir = new File(getFilesDir(), ".dsh/profiles/web");
            if (!profileDir.exists()) profileDir.mkdirs();

            File cordisPatchFile = new File(profileDir, "cordis.patch.yml");
            File packageJsonFile = new File(profileDir, "package.json");

            File targetFile = (filePath != null && !filePath.isEmpty()) ? new File(filePath) : cordisPatchFile;
            if (!targetFile.exists()) {
                try {
                    targetFile.createNewFile();
                    try (FileWriter fw = new FileWriter(targetFile)) {
                        fw.write("[]\n");
                    }
                } catch (Exception ignored) {}
            }

            StringBuilder content = new StringBuilder();
            if (targetFile.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(targetFile))) {
                    String l;
                    while ((l = br.readLine()) != null) {
                        content.append(l).append("\n");
                    }
                }
            }

            final File activeFile = targetFile;
            final EditText editor = new EditText(this);
            editor.setText(content.toString());
            editor.setTypeface(android.graphics.Typeface.MONOSPACE);
            editor.setTextSize(12);
            editor.setTextColor(0xFFE6EDF3);
            editor.setBackgroundColor(0xFF0D1117);
            editor.setPadding(24, 24, 24, 24);

            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(16, 16, 16, 16);

            // Preset selector button
            Button btnPresetLibrary = new Button(this);
            btnPresetLibrary.setText("✨ Pilih Preset Fitur Cepat (Multi-Agent, Workflow, Web, dll.)");
            btnPresetLibrary.setTextColor(0xFF58A6FF);
            btnPresetLibrary.setTextSize(12);
            btnPresetLibrary.setOnClickListener(v -> {
                String[] presetNames = new String[]{
                    "⚡ 1. Multi-Agent & Subagent Swarm (Spawn, Fork, Control)",
                    "🚀 2. Full-Power Autonomous (Multi-Agent + Workflows + Ralph Loop + Todo)",
                    "🌐 3. Web Tools & Search Integration (Fetch, Search, Web Mode)",
                    "🧹 4. Context Optimization & Auto-Pruner (Hemat Token & Compact)",
                    "👑 5. Maximum Danger / Full Access Environment Context",
                    "🔄 6. Reset Konfigurasi ke Default Kosong"
                };

                new AlertDialog.Builder(this)
                        .setTitle("✨ Preset Fitur DSH")
                        .setItems(presetNames, (d, which) -> {
                            if (which == 0) {
                                editor.setText(
                                    "# Multi-Agent & Subagent Swarm Configuration\n" +
                                    "- id: tool-subagent\n" +
                                    "  disabled: false\n" +
                                    "  config:\n" +
                                    "    provider: spawn\n" +
                                    "    toolName: subagent\n" +
                                    "    backgroundMode: continuable\n\n" +
                                    "- id: tool-subagent-fork\n" +
                                    "  disabled: false\n" +
                                    "  config:\n" +
                                    "    provider: fork\n" +
                                    "    toolName: subagent_fork\n" +
                                    "    backgroundMode: one-shot\n\n" +
                                    "- id: tool-subagent-control\n" +
                                    "  disabled: false\n\n" +
                                    "- id: tool-subagent-list-agents\n" +
                                    "  disabled: false\n"
                                );
                            } else if (which == 1) {
                                editor.setText(
                                    "# Full-Power Autonomous Agent Configuration\n" +
                                    "- id: tool-subagent\n" +
                                    "  disabled: false\n" +
                                    "  config:\n" +
                                    "    provider: spawn\n" +
                                    "    toolName: subagent\n" +
                                    "    backgroundMode: continuable\n\n" +
                                    "- id: tool-subagent-fork\n" +
                                    "  disabled: false\n" +
                                    "  config:\n" +
                                    "    provider: fork\n" +
                                    "    toolName: subagent_fork\n" +
                                    "    backgroundMode: one-shot\n\n" +
                                    "- id: tool-subagent-control\n" +
                                    "  disabled: false\n\n" +
                                    "- id: tool-subagent-list-agents\n" +
                                    "  disabled: false\n\n" +
                                    "- id: workflow-worker-thread\n" +
                                    "  disabled: false\n" +
                                    "  config:\n" +
                                    "    provider: spawn\n\n" +
                                    "- id: tool-workflow\n" +
                                    "  disabled: false\n\n" +
                                    "- id: tool-ralph\n" +
                                    "  disabled: false\n" +
                                    "  config:\n" +
                                    "    subagentProvider: spawn\n" +
                                    "    maxRounds: 64\n\n" +
                                    "- id: tool-todo\n" +
                                    "  disabled: false\n" +
                                    "  config:\n" +
                                    "    allowParallelInProgress: true\n\n" +
                                    "- id: tool-goal\n" +
                                    "  disabled: false\n"
                                );
                            } else if (which == 2) {
                                editor.setText(
                                    "# Web Tools & Search Integration\n" +
                                    "- id: tool-web\n" +
                                    "  disabled: false\n" +
                                    "  config:\n" +
                                    "    fetch: true\n" +
                                    "    searchTimeoutMs: 60000\n\n" +
                                    "- id: web\n" +
                                    "  config:\n" +
                                    "    searchProvider: deepseek-official\n"
                                );
                            } else if (which == 3) {
                                editor.setText(
                                    "# Context Optimization & Compaction\n" +
                                    "- id: tool-result-pruner\n" +
                                    "  disabled: false\n" +
                                    "  config:\n" +
                                    "    thresholdChars: 8192\n" +
                                    "    headChars: 4096\n" +
                                    "    tailChars: 1024\n\n" +
                                    "- id: spill-policy\n" +
                                    "  config:\n" +
                                    "    maxInlineBytes: 30000\n"
                                );
                            } else if (which == 4) {
                                editor.setText(
                                    "# Maximum Power & Direct System Access\n" +
                                    "- id: tools\n" +
                                    "  config:\n" +
                                    "    mode: native\n\n" +
                                    "- id: tool-str-replace-editor\n" +
                                    "  disabled: false\n" +
                                    "  config:\n" +
                                    "    maxOutputChars: 32000\n"
                                );
                            } else {
                                editor.setText("[]\n");
                            }
                            Toast.makeText(this, "Preset diterapkan ke editor! Klik 'Simpan & Restart'.", Toast.LENGTH_SHORT).show();
                        })
                        .show();
            });

            container.addView(btnPresetLibrary);

            ScrollView scroll = new ScrollView(this);
            scroll.addView(editor);
            container.addView(scroll);

            new AlertDialog.Builder(this)
                    .setTitle("⚙️ Konfigurasi (" + activeFile.getName() + ")")
                    .setView(container)
                    .setPositiveButton("Simpan & Restart", (dialog, which) -> {
                        try (FileWriter fw = new FileWriter(activeFile)) {
                            fw.write(editor.getText().toString());
                            Toast.makeText(this, "Konfigurasi disimpan! Merestart server...", Toast.LENGTH_SHORT).show();
                            restartEngine();
                        } catch (Exception e) {
                            Toast.makeText(this, "Gagal menyimpan: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNeutralButton("Pilih File Lain", (dialog, which) -> {
                        String[] configChoices = new String[]{
                            "1. cordis.patch.yml (Profil Web Overrides & Multi-Agent)",
                            "2. package.json (Daftar Dependensi & Bundle Plugin)",
                            "3. cordis.yml (Profile Root)"
                        };
                        new AlertDialog.Builder(this)
                                .setTitle("Pilih File Konfigurasi")
                                .setItems(configChoices, (d, w) -> {
                                    if (w == 0) showConfigFileViewerDialog(cordisPatchFile.getAbsolutePath());
                                    else if (w == 1) showConfigFileViewerDialog(packageJsonFile.getAbsolutePath());
                                    else showConfigFileViewerDialog(new File(profileDir, "cordis.yml").getAbsolutePath());
                                })
                                .show();
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
