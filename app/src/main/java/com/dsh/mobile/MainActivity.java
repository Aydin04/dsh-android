package com.dsh.mobile;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.graphics.drawable.IconCompat;
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

import androidx.appcompat.app.AlertDialog;

import com.dsh.mobile.assistant.FloatingBubbleService;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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
    private Button btnAtomicRoute;
    private Button btnPlugins;
    private Button btnBackup;
    private Button btnLogs;
    private Button btnConfigDoc;

    private ProgressBar progressBarPercent;
    private TextView tvProgressPercent;
    private TextView tvCurrentLogLine;
    private Button btnShowAllLogs;
    private LinearLayout logActionsLayout;
    private Button btnNode;
    private Button btnBubbleToggle;

    private static final int PERMISSION_REQ_CODE = 101;
    private static final int MANAGE_STORAGE_REQ_CODE = 102;
    private static final int FILE_CHOOSER_REQ_CODE = 103;
    private android.webkit.ValueCallback<Uri[]> uploadMessageAboveL;
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
    private final java.util.LinkedList<String> logLines = new java.util.LinkedList<>();
    private static final int MAX_LOG_LINES = 250;
    private long lastLogUiUpdateTime = 0;
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
        btnAtomicRoute = findViewById(R.id.btnAtomicRoute);
        btnPlugins = findViewById(R.id.btnPlugins);
        btnBackup = findViewById(R.id.btnBackup);
        btnLogs = findViewById(R.id.btnLogs);
        btnConfigDoc = findViewById(R.id.btnConfigDoc);
        btnNode = findViewById(R.id.btnNode);
        btnBubbleToggle = findViewById(R.id.btnBubbleToggle);

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

        createAgentNotificationChannel();
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
            StringBuilder sb = new StringBuilder();
            synchronized (logLines) {
                for (String l : logLines) {
                    sb.append(l).append("\n");
                }
            }
            ClipData cd = ClipData.newPlainText("DSH Engine Logs", sb.toString());
            cm.setPrimaryClip(cd);
            Toast.makeText(this, "Logs (" + logLines.size() + " baris) disalin!", Toast.LENGTH_SHORT).show();
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
            if (!isVisible) {
                refreshDebugLogsView();
            }
            logScrollView.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            logActionsLayout.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            btnShowAllLogs.setText(isVisible ? "📋 Detail Log" : "Sembunyikan Log");
        });

        btnLogs.setOnClickListener(v -> {
            refreshDebugLogsView();
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

        if (btnBubbleToggle != null) {
            btnBubbleToggle.setOnClickListener(v -> toggleFloatingBubble());
        }

        boolean hasAtomicRouter = false;
        try {
            String[] assets = getAssets().list("engine");
            if (assets != null) {
                for (String a : assets) {
                    if (a.startsWith("atomic-router")) {
                        hasAtomicRouter = true;
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}
        if (!hasAtomicRouter) {
            File atomicDir = new File(getFilesDir(), "atomic-router");
            if (atomicDir.exists() && (new File(atomicDir, "server.js").exists() || new File(atomicDir, "bin/omniroute.mjs").exists())) {
                hasAtomicRouter = true;
            }
        }

        if (btnAtomicRoute != null) {
            if (!hasAtomicRouter) {
                btnAtomicRoute.setVisibility(View.GONE);
            } else {
                btnAtomicRoute.setVisibility(View.VISIBLE);
                btnAtomicRoute.setOnClickListener(v -> {
                    String currentUrl = webView.getUrl();
                    if (currentUrl != null && currentUrl.contains("20128")) {
                        Toast.makeText(this, "Beralih ke DSH Chat Dashboard...", Toast.LENGTH_SHORT).show();
                        btnAtomicRoute.setText("🔀 AtomicRoute");
                        btnAtomicRoute.setTextColor(0xFFF778BA);
                        webView.loadUrl(LOCAL_URL);
                    } else {
                        Toast.makeText(this, "Membuka Full Dashboard AtomicRoute (Port 20128)...", Toast.LENGTH_SHORT).show();
                        btnAtomicRoute.setText("🤖 DSH Chat");
                        btnAtomicRoute.setTextColor(0xFF58A6FF);
                        webView.loadUrl("http://127.0.0.1:20128");
                    }
                });
            }
        }

        btnPlugins.setOnClickListener(v -> showPluginManagerDialog());

        btnBackup.setOnClickListener(v -> showBackupRestoreDialog());

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

    private void toggleFloatingBubble() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("🫧 Izin Floating Bubble")
                    .setMessage("Untuk menampilkan gelembung DeepSeek mengambang di atas aplikasi lain, silakan aktifkan izin 'Tampilkan di atas aplikasi lain'.")
                    .setPositiveButton("Beri Izin", (d, w) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    })
                    .setNegativeButton("Batal", null)
                    .show();
            return;
        }

        Intent bubbleIntent = new Intent(this, FloatingBubbleService.class);
        bubbleIntent.setAction(FloatingBubbleService.ACTION_TOGGLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(bubbleIntent);
        } else {
            startService(bubbleIntent);
        }
        Toast.makeText(this, "🫧 Floating Bubble DeepSeek Diaktifkan!", Toast.LENGTH_SHORT).show();
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
        if (message == null) return;
        synchronized (logLines) {
            logLines.add(message);
            if (logLines.size() > MAX_LOG_LINES) {
                logLines.removeFirst();
            }
        }

        // Throttle UI updates to prevent Main Thread locking during tool execution and token streaming
        long now = System.currentTimeMillis();
        if (now - lastLogUiUpdateTime > 600 || !isLoaded) {
            lastLogUiUpdateTime = now;
            handler.post(() -> {
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
                        if (tvCurrentLogLine != null && !isLoaded) {
                            tvCurrentLogLine.setText(cleanMsg);
                        }
                    }
                }

                if (debugLogs != null && debugLogs.isShown()) {
                    refreshDebugLogsView();
                }
            });
        }
    }

    private void refreshDebugLogsView() {
        if (debugLogs == null) return;
        StringBuilder sb = new StringBuilder();
        synchronized (logLines) {
            for (String l : logLines) {
                sb.append(l).append("\n");
            }
        }
        debugLogs.setText(sb.toString());
        if (logScrollView != null) {
            logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void setupWebView() {
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

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
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

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
                String failedUrl = request.getUrl().toString();
                appendLog("[WebView Error] " + errDesc + " on " + failedUrl);

                if (request.isForMainFrame() && (errDesc.contains("ERR_CONNECTION_RESET") || errDesc.contains("ERR_CONNECTION_REFUSED") || errDesc.contains("net::ERR_"))) {
                    handler.postDelayed(() -> {
                        appendLog("[WebView Retry] Retrying connection to " + failedUrl + "...");
                        view.loadUrl(failedUrl);
                    }, 1500);
                }
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try {
                if (url != null) {
                    if (url.startsWith("data:")) {
                        saveDownloadedData(url, extractFilename(url, contentDisposition, mimetype), mimetype);
                        return;
                    }
                    if (url.startsWith("blob:")) {
                        String guessedName = extractFilename(url, contentDisposition, mimetype);
                        String js = "javascript:(function() {" +
                                "  try {" +
                                "    fetch('" + url + "')" +
                                "      .then(function(r) { return r.blob(); })" +
                                "      .then(function(b) {" +
                                "        var reader = new FileReader();" +
                                "        reader.onloadend = function() {" +
                                "          if (window.AndroidBridge && window.AndroidBridge.processBlobData) {" +
                                "            window.AndroidBridge.processBlobData(reader.result, '" + guessedName + "', '" + (mimetype != null ? mimetype : "") + "');" +
                                "          }" +
                                "        };" +
                                "        reader.readAsDataURL(b);" +
                                "      })" +
                                "      .catch(function(err) {" +
                                "        console.error('Blob fetch error: ', err);" +
                                "      });" +
                                "  } catch(e) {" +
                                "    console.error('Blob handler error: ', e);" +
                                "  }" +
                                "})()";
                        runOnUiThread(() -> webView.evaluateJavascript(js, null));
                        return;
                    }
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Gagal mengunduh file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    appendLog("[WebConsole Error] " + consoleMessage.message());
                }
                return super.onConsoleMessage(consoleMessage);
            }

            // Android 5.0+ File Chooser (for Import JSON, Import Database, Config file uploads)
            @Override
            public boolean onShowFileChooser(WebView webView, android.webkit.ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (uploadMessageAboveL != null) {
                    uploadMessageAboveL.onReceiveValue(null);
                    uploadMessageAboveL = null;
                }
                uploadMessageAboveL = filePathCallback;

                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    
                    // If multiple selection is requested
                    if (fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    }

                    // Sanitize and map extensions (.json, .sqlite, .db) to valid Android SAF MIME types
                    String[] validMimeTypes = resolveValidMimeTypes(fileChooserParams.getAcceptTypes());
                    if (validMimeTypes != null && validMimeTypes.length > 0 && !validMimeTypes[0].equals("*/*")) {
                        intent.putExtra(Intent.EXTRA_MIME_TYPES, validMimeTypes);
                    }

                    startActivityForResult(Intent.createChooser(intent, "Pilih File Import"), FILE_CHOOSER_REQ_CODE);
                    return true;
                } catch (Exception e) {
                    try {
                        // Fallback to ACTION_GET_CONTENT
                        Intent fallbackIntent = new Intent(Intent.ACTION_GET_CONTENT);
                        fallbackIntent.addCategory(Intent.CATEGORY_OPENABLE);
                        fallbackIntent.setType("*/*");

                        String[] validMimeTypes = resolveValidMimeTypes(fileChooserParams.getAcceptTypes());
                        if (validMimeTypes != null && validMimeTypes.length > 0 && !validMimeTypes[0].equals("*/*")) {
                            fallbackIntent.putExtra(Intent.EXTRA_MIME_TYPES, validMimeTypes);
                        }

                        startActivityForResult(Intent.createChooser(fallbackIntent, "Pilih File Import"), FILE_CHOOSER_REQ_CODE);
                        return true;
                    } catch (Exception ex) {
                        if (uploadMessageAboveL != null) {
                            uploadMessageAboveL.onReceiveValue(null);
                            uploadMessageAboveL = null;
                        }
                        Toast.makeText(MainActivity.this, "Gagal membuka File Picker: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }
            }
        });
    }

    private String[] resolveValidMimeTypes(String[] acceptTypes) {
        if (acceptTypes == null || acceptTypes.length == 0) {
            return new String[]{"*/*"};
        }

        java.util.Set<String> mimeSet = new java.util.LinkedHashSet<>();
        for (String raw : acceptTypes) {
            if (raw == null) continue;
            String[] parts = raw.split(",");
            for (String item : parts) {
                String clean = item.trim().toLowerCase();
                if (clean.isEmpty()) continue;

                if (clean.startsWith(".")) {
                    String ext = clean.substring(1);
                    if (ext.equals("json") || ext.equals("jsonl")) {
                        mimeSet.add("application/json");
                        mimeSet.add("text/plain");
                        mimeSet.add("application/octet-stream");
                    } else if (ext.equals("sqlite") || ext.equals("sqlite3") || ext.equals("db")) {
                        mimeSet.add("application/x-sqlite3");
                        mimeSet.add("application/vnd.sqlite3");
                        mimeSet.add("application/octet-stream");
                    } else if (ext.equals("toml")) {
                        mimeSet.add("text/plain");
                        mimeSet.add("application/toml");
                        mimeSet.add("application/octet-stream");
                    } else if (ext.equals("har")) {
                        mimeSet.add("application/json");
                        mimeSet.add("text/plain");
                        mimeSet.add("application/octet-stream");
                    } else if (ext.equals("csv")) {
                        mimeSet.add("text/csv");
                        mimeSet.add("text/plain");
                    } else if (ext.equals("zip")) {
                        mimeSet.add("application/zip");
                        mimeSet.add("application/x-zip-compressed");
                        mimeSet.add("application/octet-stream");
                    } else if (ext.equals("tar") || ext.equals("gz") || ext.equals("tgz")) {
                        mimeSet.add("application/gzip");
                        mimeSet.add("application/x-tar");
                        mimeSet.add("application/octet-stream");
                    } else {
                        String fromMap = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                        if (fromMap != null && !fromMap.isEmpty()) {
                            mimeSet.add(fromMap);
                        }
                        mimeSet.add("application/octet-stream");
                    }
                } else if (clean.contains("/")) {
                    mimeSet.add(clean);
                    if (clean.equals("application/json")) {
                        mimeSet.add("text/plain");
                        mimeSet.add("application/octet-stream");
                    }
                }
            }
        }

        if (mimeSet.isEmpty() || mimeSet.contains("*/*")) {
            return new String[]{"*/*"};
        }
        return mimeSet.toArray(new String[0]);
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
                "  if (window.__dshAgentNotifierPatched) return;" +
                "  window.__dshAgentNotifierPatched = true;" +
                "  var isWaitingForAgent = false;" +
                "  var lastNotifiedText = '';" +
                "  document.addEventListener('keydown', function(e) {" +
                "    if (e.key === 'Enter' && !e.shiftKey && !e.ctrlKey && !e.altKey && !e.metaKey) {" +
                "      var target = e.target;" +
                "      if (target && (target.tagName === 'TEXTAREA' || target.isContentEditable || target.getAttribute('role') === 'textbox')) {" +
                "        e.stopPropagation();" +
                "        isWaitingForAgent = true;" +
                "      }" +
                "    }" +
                "  }, true);" +
                "  document.addEventListener('click', function(e) {" +
                "    var btn = e.target.closest('button, [role=\"button\"]');" +
                "    if (btn) {" +
                "      var label = (btn.getAttribute('aria-label') || btn.title || btn.innerText || '').toLowerCase();" +
                "      if (label.includes('send') || label.includes('kirim') || btn.querySelector('svg')) {" +
                "        isWaitingForAgent = true;" +
                "      }" +
                "    }" +
                "  }, true);" +
                "  function isGeneratingActive() {" +
                "    var stopBtn = document.querySelector('button[aria-label*=\"Stop\" i], button[title*=\"Stop\" i], [class*=\"stop\" i], [class*=\"abort\" i]');" +
                "    var spinners = document.querySelectorAll('[class*=\"cursor\" i], [class*=\"typing\" i], [class*=\"loading\" i], [class*=\"spinner\" i], [class*=\"progress\" i]');" +
                "    return (stopBtn !== null || spinners.length > 0);" +
                "  }" +
                "  function getCleanAssistantAnswer() {" +
                "    var messages = document.querySelectorAll('article, [data-role=\"assistant\"], [class*=\"assistant\" i], [class*=\"message\" i]');" +
                "    if (!messages || messages.length === 0) return '';" +
                "    var lastMsg = messages[messages.length - 1];" +
                "    var clone = lastMsg.cloneNode(true);" +
                "    var thinkingNodes = clone.querySelectorAll('[class*=\"thought\" i], [class*=\"thinking\" i], [class*=\"status\" i], [class*=\"step\" i], [class*=\"trajectory\" i], [class*=\"deep\" i]');" +
                "    thinkingNodes.forEach(function(n) { n.remove(); });" +
                "    return (clone.innerText || clone.textContent || '').trim();" +
                "  }" +
                "  function checkTurnCompletion() {" +
                "    try {" +
                "      if (isGeneratingActive()) {" +
                "        isWaitingForAgent = true;" +
                "        return;" +
                "      }" +
                "      if (isWaitingForAgent) {" +
                "        var answer = getCleanAssistantAnswer();" +
                "        if (answer.length > 6 && !answer.toLowerCase().startsWith('deep diving') && !answer.toLowerCase().startsWith('thinking')) {" +
                "          if (answer !== lastNotifiedText) {" +
                "            lastNotifiedText = answer;" +
                "            isWaitingForAgent = false;" +
                "            if (window.AndroidBridge && window.AndroidBridge.notifyAgentReply) {" +
                "              window.AndroidBridge.notifyAgentReply(answer);" +
                "            }" +
                "          }" +
                "        }" +
                "      }" +
                "    } catch(e) {}" +
                "  }" +
                "  setInterval(checkTurnCompletion, 1000);" +
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
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

    private static final String AGENT_REPLY_CHANNEL_ID = "DSH_AGENT_REPLY_CHANNEL";

    private void createAgentNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    AGENT_REPLY_CHANNEL_ID,
                    "Balasan Agent AI",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifikasi balasan selesai dari Agent DeepSeek Harness.");
            channel.enableVibration(true);
            channel.enableLights(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                channel.setAllowBubbles(true);
            }
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    public void showAgentReplyNotification(String replyText) {
        if (replyText == null || replyText.trim().isEmpty()) return;

        // Clean markdown tags for clear notification preview
        String preview = replyText.replaceAll("[#*`_>~]", "").replaceAll("\\s+", " ").trim();
        if (preview.length() > 140) {
            preview = preview.substring(0, 137) + "...";
        }

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, 1004, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Person agentPerson = new Person.Builder()
                .setName("DeepSeek Agent")
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_deepseek))
                .setBot(true)
                .build();

        NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle(agentPerson)
                .setConversationTitle("DeepSeek Harness")
                .addMessage(replyText, System.currentTimeMillis(), agentPerson);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, AGENT_REPLY_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_deepseek)
                .setContentTitle("🤖 DeepSeek Agent Selesai")
                .setContentText(preview)
                .setStyle(messagingStyle)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setDefaults(Notification.DEFAULT_ALL);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(1005, builder.build());
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

        @JavascriptInterface
        public void processBlobData(String base64Data, String filename, String mimetype) {
            saveDownloadedData(base64Data, filename, mimetype);
        }

        @JavascriptInterface
        public void notifyAgentReply(String messagePreview) {
            showAgentReplyNotification(messagePreview);
        }
    }

    private String extractFilename(String url, String contentDisposition, String mimetype) {
        String filename = null;
        try {
            filename = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype);
        } catch (Exception ignored) {}
        if (filename == null || filename.isEmpty() || filename.equals("downloadfile.bin") || filename.startsWith("downloadfile")) {
            if (contentDisposition != null) {
                int fnIndex = contentDisposition.toLowerCase().indexOf("filename=");
                if (fnIndex != -1) {
                    String fn = contentDisposition.substring(fnIndex + 9).trim().replace("\"", "").replace("'", "");
                    if (fn.contains(";")) fn = fn.substring(0, fn.indexOf(";"));
                    if (!fn.isEmpty()) filename = fn;
                }
            }
        }
        if (filename == null || filename.isEmpty() || filename.equals("downloadfile.bin") || filename.startsWith("downloadfile")) {
            String ext = ".json";
            if (mimetype != null && mimetype.contains("sqlite")) ext = ".sqlite";
            else if (mimetype != null && (mimetype.contains("tar") || mimetype.contains("gzip"))) ext = ".tar.gz";
            else if (mimetype != null && mimetype.contains("csv")) ext = ".csv";
            filename = "atomicroute_export_" + System.currentTimeMillis() + ext;
        }
        return filename;
    }

    private void saveDownloadedData(String dataUri, String filename, String mimetype) {
        if (dataUri == null || dataUri.isEmpty()) return;
        try {
            int commaIndex = dataUri.indexOf(",");
            byte[] decodedData;
            if (commaIndex != -1) {
                String meta = dataUri.substring(0, commaIndex);
                String rawData = dataUri.substring(commaIndex + 1);
                if (meta.contains(";base64")) {
                    decodedData = android.util.Base64.decode(rawData, android.util.Base64.DEFAULT);
                } else {
                    decodedData = java.net.URLDecoder.decode(rawData, "UTF-8").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }
            } else {
                decodedData = android.util.Base64.decode(dataUri, android.util.Base64.DEFAULT);
            }

            File backupDir = new File(Environment.getExternalStorageDirectory(), "DSH_Backups");
            if (!backupDir.exists()) backupDir.mkdirs();

            String finalName = filename;
            if (finalName == null || finalName.isEmpty() || finalName.equals("null")) {
                String ext = ".json";
                if (dataUri.contains("application/x-sqlite") || (mimetype != null && mimetype.contains("sqlite"))) {
                    ext = ".sqlite";
                } else if (dataUri.contains("tar") || dataUri.contains("gzip") || (mimetype != null && mimetype.contains("gzip"))) {
                    ext = ".tar.gz";
                }
                finalName = "atomicroute_export_" + System.currentTimeMillis() + ext;
            }

            File targetFile = new File(backupDir, finalName);
            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                fos.write(decodedData);
            }

            handler.post(() -> Toast.makeText(MainActivity.this, "✅ File diekspor ke: /sdcard/DSH_Backups/" + targetFile.getName(), Toast.LENGTH_LONG).show());
        } catch (Exception e) {
            handler.post(() -> Toast.makeText(MainActivity.this, "Gagal menyimpan file ekspor: " + e.getMessage(), Toast.LENGTH_SHORT).show());
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

    private void showBackupRestoreDialog() {
        File filesDir = getFilesDir();
        File dshDataDir = new File(filesDir, ".dsh");
        File externalStorage = Environment.getExternalStorageDirectory();
        File backupRootDir = new File(externalStorage != null ? externalStorage : new File("/sdcard"), "DSH_Backups");
        if (!backupRootDir.exists()) backupRootDir.mkdirs();

        String[] options = new String[]{
            "💾 1. Backup Lengkap DSH & AtomicRoute (.tar.gz)",
            "📂 2. Restore Lengkap dari File Backup (.tar.gz)",
            "📤 3. Ekspor Database AtomicRoute (storage.sqlite)",
            "📥 4. Impor Database AtomicRoute Langsung (storage.sqlite)",
            "📋 5. Informasi Lokasi Database & Direktori Penyimpanan"
        };

        new AlertDialog.Builder(this)
                .setTitle("💾 Backup, Restore, & Database Manager")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        performDshBackup(backupRootDir, dshDataDir);
                    } else if (which == 1) {
                        showRestoreListDialog(backupRootDir, dshDataDir);
                    } else if (which == 2) {
                        exportAtomicSQLiteDirect(backupRootDir);
                    } else if (which == 3) {
                        importAtomicSQLiteDirect(backupRootDir);
                    } else {
                        showStorageLocationsInfo(dshDataDir, backupRootDir);
                    }
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    private void performDshBackup(File backupRootDir, File dshDataDir) {
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(new java.util.Date());
        File targetBackupFile = new File(backupRootDir, "dsh_backup_" + timestamp + ".tar.gz");
        File atomicDataDir = new File(getFilesDir(), ".atomic-router");

        Toast.makeText(this, "Membuat backup ke /sdcard/DSH_Backups/...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                if (!dshDataDir.exists() && !atomicDataDir.exists()) {
                    handler.post(() -> Toast.makeText(this, "Belum ada data DSH / AtomicRouter untuk dibackup.", Toast.LENGTH_SHORT).show());
                    return;
                }

                try (FileOutputStream fos = new FileOutputStream(targetBackupFile);
                     java.util.zip.GZIPOutputStream gos = new java.util.zip.GZIPOutputStream(fos);
                     org.apache.commons.compress.archivers.tar.TarArchiveOutputStream tos = new org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(gos)) {
                    tos.setLongFileMode(org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.LONGFILE_POSIX);
                    if (dshDataDir.exists()) addDirectoryToTar(tos, dshDataDir, ".dsh");
                    if (atomicDataDir.exists()) addDirectoryToTar(tos, atomicDataDir, ".atomic-router");
                }

                handler.post(() -> {
                    new AlertDialog.Builder(this)
                            .setTitle("✅ Backup Berhasil!")
                            .setMessage("Seluruh database DSH (sesi & config) dan database AtomicRouter (API keys & accounts) telah tersimpan aman di:\n\n📁 " + targetBackupFile.getAbsolutePath() + "\nUkuran: " + (targetBackupFile.length() / 1024) + " KB")
                            .setPositiveButton("Bagus", null)
                            .show();
                });
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(this, "Gagal membuat backup: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void addDirectoryToTar(org.apache.commons.compress.archivers.tar.TarArchiveOutputStream tos, File dir, String basePath) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String entryName = basePath.isEmpty() ? f.getName() : basePath + "/" + f.getName();
            if (f.isDirectory()) {
                if (entryName.contains("node_modules")) continue; // skip heavy node_modules for ultra-fast light backup
                org.apache.commons.compress.archivers.tar.TarArchiveEntry entry = new org.apache.commons.compress.archivers.tar.TarArchiveEntry(f, entryName + "/");
                tos.putArchiveEntry(entry);
                tos.closeArchiveEntry();
                addDirectoryToTar(tos, f, entryName);
            } else {
                org.apache.commons.compress.archivers.tar.TarArchiveEntry entry = new org.apache.commons.compress.archivers.tar.TarArchiveEntry(f, entryName);
                entry.setSize(f.length());
                tos.putArchiveEntry(entry);
                try (FileInputStream fis = new FileInputStream(f)) {
                    byte[] buf = new byte[16384];
                    int len;
                    while ((len = fis.read(buf)) != -1) {
                        tos.write(buf, 0, len);
                    }
                }
                tos.closeArchiveEntry();
            }
        }
    }

    private void showRestoreListDialog(File backupRootDir, File dshDataDir) {
        File[] backups = backupRootDir.listFiles((dir, name) -> name.endsWith(".tar.gz") && name.startsWith("dsh_backup_"));
        if (backups == null || backups.length == 0) {
            Toast.makeText(this, "Tidak ada file backup di /sdcard/DSH_Backups/", Toast.LENGTH_LONG).show();
            return;
        }

        String[] backupItems = new String[backups.length];
        for (int i = 0; i < backups.length; i++) {
            backupItems[i] = "📦 " + backups[i].getName() + " (" + (backups[i].length() / 1024) + " KB)";
        }

        new AlertDialog.Builder(this)
                .setTitle("Pilih File Backup untuk Dipulihkan")
                .setItems(backupItems, (dialog, which) -> {
                    File selectedBackup = backups[which];
                    new AlertDialog.Builder(this)
                            .setTitle("Konfirmasi Restore Data")
                            .setMessage("Apakah Anda yakin ingin memulihkan data dari " + selectedBackup.getName() + "?\nData konfigurasi, sesi, dan database akun AtomicRouter akan ditimpa.")
                            .setPositiveButton("Ya, Pulihkan & Restart", (d, w) -> performRestore(selectedBackup, getFilesDir()))
                            .setNegativeButton("Batal", null)
                            .show();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void performRestore(File backupFile, File targetBaseDir) {
        Toast.makeText(this, "Sedang memulihkan data...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                try (FileInputStream fis = new FileInputStream(backupFile);
                     java.util.zip.GZIPInputStream gis = new java.util.zip.GZIPInputStream(fis);
                     org.apache.commons.compress.archivers.tar.TarArchiveInputStream tis = new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(gis)) {
                    org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
                    while ((entry = tis.getNextTarEntry()) != null) {
                        File target = new File(targetBaseDir, entry.getName());
                        if (entry.isDirectory()) {
                            target.mkdirs();
                        } else {
                            File parent = target.getParentFile();
                            if (parent != null && !parent.exists()) parent.mkdirs();
                            try (FileOutputStream fos = new FileOutputStream(target)) {
                                byte[] buf = new byte[16384];
                                int len;
                                while ((len = tis.read(buf)) != -1) {
                                    fos.write(buf, 0, len);
                                }
                            }
                        }
                    }
                }

                handler.post(() -> {
                    Toast.makeText(this, "Data DSH & AtomicRouter berhasil dipulihkan! Me-restart server...", Toast.LENGTH_LONG).show();
                    restartEngine();
                });
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(this, "Gagal restore: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void showStorageLocationsInfo(File dshDataDir, File backupRootDir) {
        File atomicDataDir = new File(getFilesDir(), ".atomic-router");
        String info = 
            "📂 Lokasi Database & Penyimpanan Internal:\n\n" +
            "1. Database Sessions / Riwayat Chat DSH:\n" +
            "   " + dshDataDir.getAbsolutePath() + "/storages/sessions/\n\n" +
            "2. Database Akun, Keys, & History AtomicRouter:\n" +
            "   " + atomicDataDir.getAbsolutePath() + "/\n\n" +
            "3. Profile Web & Konfigurasi Plugin:\n" +
            "   " + dshDataDir.getAbsolutePath() + "/profiles/web/\n\n" +
            "4. Lokasi Penyimpanan Backup Eksternal:\n" +
            "   " + backupRootDir.getAbsolutePath() + "/\n\n" +
            "5. Primary Workspace User:\n" +
            "   /sdcard (Penyimpanan Internal Utama HP)";

        new AlertDialog.Builder(this)
                .setTitle("ℹ️ Lokasi Database & Storage")
                .setMessage(info)
                .setPositiveButton("Tutup", null)
                .show();
    }

    private void exportAtomicSQLiteDirect(File backupRootDir) {
        File atomicDb = new File(getFilesDir(), ".atomic-router/storage.sqlite");
        if (!atomicDb.exists()) {
            Toast.makeText(this, "Database AtomicRoute belum dibuat di storage internal.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(new java.util.Date());
            File targetExport = new File(backupRootDir, "atomic_storage_" + timestamp + ".sqlite");
            try (FileInputStream in = new FileInputStream(atomicDb);
                 FileOutputStream out = new FileOutputStream(targetExport)) {
                byte[] buf = new byte[16384];
                int len;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
            }
            new AlertDialog.Builder(this)
                    .setTitle("📤 Ekspor Database Berhasil")
                    .setMessage("File database SQLite AtomicRoute telah berhasil diekspor ke:\n\n📁 " + targetExport.getAbsolutePath() + "\n\nAnda dapat menyimpannya atau memindahkannya kapan saja.")
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "Gagal ekspor: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void importAtomicSQLiteDirect(File backupRootDir) {
        File[] dbFiles = backupRootDir.listFiles((dir, name) -> name.endsWith(".sqlite") || name.endsWith(".db"));
        if (dbFiles == null || dbFiles.length == 0) {
            new AlertDialog.Builder(this)
                    .setTitle("📥 Impor Database AtomicRoute")
                    .setMessage("Tidak ditemukan file .sqlite atau .db di folder:\n" + backupRootDir.getAbsolutePath() + "\n\nSilakan letakkan file database Anda di folder tersebut terlebih dahulu.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        String[] items = new String[dbFiles.length];
        for (int i = 0; i < dbFiles.length; i++) {
            items[i] = "🗄️ " + dbFiles[i].getName() + " (" + (dbFiles[i].length() / 1024) + " KB)";
        }

        new AlertDialog.Builder(this)
                .setTitle("Pilih Database untuk Diimpor")
                .setItems(items, (dialog, which) -> {
                    File selectedDb = dbFiles[which];
                    new AlertDialog.Builder(this)
                            .setTitle("Konfirmasi Impor Database")
                            .setMessage("Apakah Anda ingin mengganti database aktif AtomicRoute dengan file " + selectedDb.getName() + "?\nServer akan di-restart otomatis.")
                            .setPositiveButton("Ya, Timpa & Restart", (d, w) -> {
                                try {
                                    File targetDir = new File(getFilesDir(), ".atomic-router");
                                    if (!targetDir.exists()) targetDir.mkdirs();
                                    File targetDb = new File(targetDir, "storage.sqlite");
                                    try (FileInputStream in = new FileInputStream(selectedDb);
                                         FileOutputStream out = new FileOutputStream(targetDb)) {
                                        byte[] buf = new byte[16384];
                                        int len;
                                        while ((len = in.read(buf)) != -1) {
                                            out.write(buf, 0, len);
                                        }
                                    }
                                    Toast.makeText(this, "✅ Database sukses diimpor! Me-restart server...", Toast.LENGTH_LONG).show();
                                    restartEngine();
                                } catch (Exception e) {
                                    Toast.makeText(this, "Gagal impor: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setNegativeButton("Batal", null)
                            .show();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQ_CODE) {
            if (uploadMessageAboveL != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && data != null) {
                    String dataString = data.getDataString();
                    android.content.ClipData clipData = data.getClipData();
                    if (clipData != null) {
                        results = new Uri[clipData.getItemCount()];
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            results[i] = clipData.getItemAt(i).getUri();
                        }
                    } else if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }
                uploadMessageAboveL.onReceiveValue(results);
                uploadMessageAboveL = null;
            }
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(engineReceiver);
        super.onDestroy();
    }
}
