package com.dsh.mobile.assistant;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.dsh.mobile.MainActivity;
import com.dsh.mobile.R;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;

public class AssistActivity extends Activity {

    private static final String PREFS_NAME = "DSH_ASSISTANT_PREFS";
    private static final String DSH_URL = "http://127.0.0.1:3080";

    private LinearLayout assistantBottomSheet;
    private Button btnPresetsDropdown;
    private Button btnCronDropdown;
    private ImageButton btnAttachScreenshot;
    private ImageButton btnExpandFull;
    private ImageButton btnCloseAssistant;

    private WebView assistantWebView;
    private ProgressBar assistantWebLoading;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private File lastScreenshotFile = null;

    // Preset States
    private boolean isGameBoostOn = false;
    private boolean isBatterySaverOn = false;
    private boolean isAirplaneOn = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assistant_popup);

        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.gravity = Gravity.BOTTOM;
        getWindow().setAttributes(lp);

        initViews();
        setupListeners();
        setupWebView();
        loadPresetStates();

        // 1. Instant fresh screen capture on assistant launch
        takeFreshScreenshotAndShowUI(false);
    }

    private void initViews() {
        assistantBottomSheet = findViewById(R.id.assistantBottomSheet);
        btnPresetsDropdown = findViewById(R.id.btnPresetsDropdown);
        btnCronDropdown = findViewById(R.id.btnCronDropdown);
        btnAttachScreenshot = findViewById(R.id.btnAttachScreenshot);
        btnExpandFull = findViewById(R.id.btnExpandFull);
        btnCloseAssistant = findViewById(R.id.btnCloseAssistant);
        assistantWebView = findViewById(R.id.assistantWebView);
        assistantWebLoading = findViewById(R.id.assistantWebLoading);
    }

    private void loadPresetStates() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isGameBoostOn = prefs.getBoolean("PRESET_GAME_BOOST", false);
        isBatterySaverOn = prefs.getBoolean("PRESET_BATTERY_SAVER", false);
        isAirplaneOn = prefs.getBoolean("PRESET_AIRPLANE", false);
    }

    private void setupListeners() {
        btnPresetsDropdown.setOnClickListener(v -> showPresetsDropdownDialog());

        btnCronDropdown.setOnClickListener(v -> showCronDropdownDialog());

        btnAttachScreenshot.setOnClickListener(v -> {
            Toast.makeText(this, "📸 Mengambil screenshot layar bersih...", Toast.LENGTH_SHORT).show();
            takeFreshScreenshotAndShowUI(true);
        });

        btnExpandFull.setOnClickListener(v -> {
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(mainIntent);
            finish();
        });

        btnCloseAssistant.setOnClickListener(v -> finish());
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        assistantWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings settings = assistantWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setTextZoom(100);
        settings.setUseWideViewPort(false);
        settings.setLoadWithOverviewMode(false);

        assistantWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                assistantWebLoading.setVisibility(View.GONE);
                injectMobileViewportAndEnterKey(view);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    handler.postDelayed(() -> view.loadUrl(DSH_URL), 1500);
                }
            }
        });

        assistantWebView.setWebChromeClient(new WebChromeClient());
        assistantWebView.loadUrl(DSH_URL);
    }

    private void injectMobileViewportAndEnterKey(WebView view) {
        String js = "javascript:(function() {" +
                "  try {" +
                "    document.addEventListener('keydown', function(e) {" +
                "      if (e.key === 'Enter' && !e.shiftKey && !e.ctrlKey && !e.altKey) {" +
                "        var active = document.activeElement;" +
                "        if (active && (active.tagName === 'TEXTAREA' || active.isContentEditable)) {" +
                "          e.stopPropagation();" +
                "        }" +
                "      }" +
                "    }, true);" +
                "    window.injectDshAttachment = function(filename) {" +
                "      var textarea = document.querySelector('textarea') || document.querySelector('[contenteditable=\"true\"]');" +
                "      if (textarea) {" +
                "        var textToInsert = '@' + filename + ' Jelaskan isi gambar layar ini: ';" +
                "        if (textarea.tagName === 'TEXTAREA') {" +
                "          textarea.value = textToInsert + (textarea.value || '');" +
                "          textarea.dispatchEvent(new Event('input', { bubbles: true }));" +
                "        } else {" +
                "          textarea.innerText = textToInsert + (textarea.innerText || '');" +
                "          textarea.dispatchEvent(new Event('input', { bubbles: true }));" +
                "        }" +
                "        textarea.focus();" +
                "      }" +
                "    };" +
                "  } catch(e) {}" +
                "})()";
        view.evaluateJavascript(js, null);
    }

    private void takeFreshScreenshotAndShowUI(boolean autoInjectAfterCapture) {
        if (autoInjectAfterCapture) {
            assistantBottomSheet.setVisibility(View.INVISIBLE);
        }

        handler.postDelayed(() -> new Thread(() -> {
            try {
                File targetDir = new File(getExternalFilesDir(null) != null ? getExternalFilesDir(null) : getFilesDir(), "dsh_screenshots");
                if (!targetDir.exists()) targetDir.mkdirs();

                File[] oldFiles = targetDir.listFiles();
                if (oldFiles != null) {
                    for (File f : oldFiles) f.delete();
                }

                lastScreenshotFile = new File(targetDir, "current_screen.png");

                // Execute screencap via root
                runRootCommand("screencap -p " + lastScreenshotFile.getAbsolutePath() + " && cp " + lastScreenshotFile.getAbsolutePath() + " /sdcard/current_screen.png 2>/dev/null || true");

                handler.post(() -> {
                    assistantBottomSheet.setVisibility(View.VISIBLE);
                    if (autoInjectAfterCapture && lastScreenshotFile != null && lastScreenshotFile.exists()) {
                        assistantWebView.evaluateJavascript("javascript:if(window.injectDshAttachment) { window.injectDshAttachment('" + lastScreenshotFile.getName() + "'); }", null);
                        Toast.makeText(this, "📸 Tangkapan layar disisipkan ke DSH!", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                handler.post(() -> assistantBottomSheet.setVisibility(View.VISIBLE));
            }
        }).start(), autoInjectAfterCapture ? 150 : 0);
    }

    private void showPresetsDropdownDialog() {
        String[] items = new String[]{
                (isGameBoostOn ? "🟢 [ON] 🎮 Game Boost Mode" : "⚪ [OFF] 🎮 Game Boost Mode"),
                (isBatterySaverOn ? "🟢 [ON] 🔋 Extreme Battery Saver" : "⚪ [OFF] 🔋 Extreme Battery Saver"),
                "⚡ [RUN] 🧹 Deep Clean RAM & Cache",
                (isAirplaneOn ? "🟢 [ON] ✈️ Airplane Mode" : "⚪ [OFF] ✈️ Airplane Mode"),
                "⚡ [RUN] 📡 Toggle WiFi Radio"
        };

        new AlertDialog.Builder(this)
                .setTitle("⚡ Presets & Quick Mode Manager")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        isGameBoostOn = !isGameBoostOn;
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean("PRESET_GAME_BOOST", isGameBoostOn).apply();
                        if (isGameBoostOn) {
                            runRootCommand("echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null; sync; echo 3 > /proc/sys/vm/drop_caches");
                            Toast.makeText(this, "🎮 Game Boost [ON]: CPU Performance", Toast.LENGTH_SHORT).show();
                        } else {
                            runRootCommand("echo schedutil > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null");
                            Toast.makeText(this, "🎮 Game Boost [OFF]: CPU Schedutil", Toast.LENGTH_SHORT).show();
                        }
                    } else if (which == 1) {
                        isBatterySaverOn = !isBatterySaverOn;
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean("PRESET_BATTERY_SAVER", isBatterySaverOn).apply();
                        if (isBatterySaverOn) {
                            runRootCommand("settings put system screen_brightness 20; echo powersave > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null");
                            Toast.makeText(this, "🔋 Battery Saver [ON]", Toast.LENGTH_SHORT).show();
                        } else {
                            runRootCommand("echo schedutil > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null");
                            Toast.makeText(this, "🔋 Battery Saver [OFF]", Toast.LENGTH_SHORT).show();
                        }
                    } else if (which == 2) {
                        runRootCommand("sync; echo 3 > /proc/sys/vm/drop_caches");
                        Toast.makeText(this, "🧹 Deep RAM Clean Selesai!", Toast.LENGTH_SHORT).show();
                    } else if (which == 3) {
                        isAirplaneOn = !isAirplaneOn;
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean("PRESET_AIRPLANE", isAirplaneOn).apply();
                        runRootCommand("settings put global airplane_mode_on " + (isAirplaneOn ? "1" : "0") + " && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state " + isAirplaneOn);
                        Toast.makeText(this, "✈️ Airplane Mode [" + (isAirplaneOn ? "ON" : "OFF") + "]", Toast.LENGTH_SHORT).show();
                    } else if (which == 4) {
                        runRootCommand("svc wifi disable 2>/dev/null || svc wifi enable 2>/dev/null");
                        Toast.makeText(this, "📡 WiFi Radio Toggled", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    private void showCronDropdownDialog() {
        String[] options = new String[]{
                "✈️ Mode Pesawat 10 Menit Lagi",
                "🧹 Clean RAM Otomatis Setiap 1 Jam",
                "🌙 Mode Hemat Baterai (Malam 22:00)",
                "⏰ Jadwalkan Tugas / Perintah Kustom..."
        };

        new AlertDialog.Builder(this)
                .setTitle("⏰ Cron & Scheduled Tasks")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        scheduleRootTask(10 * 60 * 1000, "settings put global airplane_mode_on 1 && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true", "Mode Pesawat (10m)");
                    } else if (which == 1) {
                        scheduleRootTask(60 * 60 * 1000, "sync; echo 3 > /proc/sys/vm/drop_caches", "Clean RAM (1 Jam)");
                    } else if (which == 2) {
                        scheduleRootTask(15 * 60 * 1000, "settings put system screen_brightness 10", "Dim Layar (15m)");
                    } else {
                        Toast.makeText(this, "Jadwalkan kustom langsung via chat DSH!", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    private void scheduleRootTask(long delayMs, String cmd, String label) {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AssistantAlarmReceiver.class);
        intent.putExtra("COMMAND", cmd);
        intent.putExtra("LABEL", label);

        PendingIntent pi = PendingIntent.getBroadcast(
                this,
                (int) System.currentTimeMillis(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        long triggerAt = System.currentTimeMillis() + delayMs;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }

        Toast.makeText(this, "⏰ Jadwal " + label + " disimpan!", Toast.LENGTH_SHORT).show();
    }

    private String runRootCommand(String command) {
        StringBuilder output = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            p.waitFor();
        } catch (Exception e) {
            output.append("Error: ").append(e.getMessage());
        }
        return output.toString().trim();
    }

    @Override
    protected void onDestroy() {
        if (assistantWebView != null) {
            assistantWebView.destroy();
        }
        super.onDestroy();
    }
}
