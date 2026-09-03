package com.dsh.mobile.assistant;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.dsh.mobile.MainActivity;
import com.dsh.mobile.R;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FloatingBubbleService extends Service {

    public static final String ACTION_START = "ACTION_START";
    public static final String ACTION_STOP = "ACTION_STOP";
    public static final String ACTION_TOGGLE = "ACTION_TOGGLE";

    private static final String CHANNEL_ID = "DSH_BUBBLE_CHANNEL";
    private static final String PREFS_NAME = "DSH_BUBBLE_PREFS";
    private static final String DSH_URL = "http://127.0.0.1:3080";

    private WindowManager windowManager;
    private View bubbleView;
    private View windowView;

    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams windowParams;

    private WebView bubbleWebView;
    private ProgressBar bubbleWebLoading;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isWindowExpanded = false;
    private boolean isLargeWindow = false;

    // Presets
    private boolean isGameBoostOn = false;
    private boolean isBatterySaverOn = false;
    private boolean isAirplaneOn = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1003, buildNotification());

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        loadPresetStates();
        initBubbleView();
        initWindowView();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "DSH Floating Bubble",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DSH Floating Bubble Aktif")
                .setContentText("Ketuk bubble untuk membuka asisten AI")
                .setSmallIcon(R.drawable.ic_deepseek)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void loadPresetStates() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isGameBoostOn = prefs.getBoolean("PRESET_GAME_BOOST", false);
        isBatterySaverOn = prefs.getBoolean("PRESET_BATTERY_SAVER", false);
        isAirplaneOn = prefs.getBoolean("PRESET_AIRPLANE", false);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initBubbleView() {
        bubbleView = LayoutInflater.from(this).inflate(R.layout.layout_floating_bubble, null);

        int layoutType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        bubbleParams = new WindowManager.LayoutParams(
                dpToPx(56),
                dpToPx(56),
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = 20;
        bubbleParams.y = dpToPx(180);

        bubbleView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDrag = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = bubbleParams.x;
                        initialY = bubbleParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDrag = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDrag = true;
                            bubbleParams.x = initialX + dx;
                            bubbleParams.y = initialY + dy;
                            windowManager.updateViewLayout(bubbleView, bubbleParams);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isDrag) {
                            expandWindow();
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(bubbleView, bubbleParams);
        // Keep windowView attached with 1x1 offscreen layout so WebView JS never freezes in background
        windowParams.width = 1;
        windowParams.height = 1;
        windowParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        windowView.setVisibility(View.INVISIBLE);
        try {
            windowManager.addView(windowView, windowParams);
        } catch (Exception ignored) {}
    }

    @SuppressLint({"ClickableViewAccessibility", "SetJavaScriptEnabled"})
    private void initWindowView() {
        windowView = LayoutInflater.from(this).inflate(R.layout.layout_floating_window, null);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int defaultWidth = Math.min(dm.widthPixels - dpToPx(24), dpToPx(380));
        int defaultHeight = dpToPx(480);

        int layoutType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        windowParams = new WindowManager.LayoutParams(
                defaultWidth,
                defaultHeight,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );

        windowParams.gravity = Gravity.CENTER;

        View headerBar = windowView.findViewById(R.id.windowHeaderBar);
        Button btnBubblePresets = windowView.findViewById(R.id.btnBubblePresets);
        ImageButton btnBubbleScreenshot = windowView.findViewById(R.id.btnBubbleScreenshot);
        ImageButton btnBubbleResize = windowView.findViewById(R.id.btnBubbleResize);
        ImageButton btnBubbleMinimize = windowView.findViewById(R.id.btnBubbleMinimize);

        bubbleWebView = windowView.findViewById(R.id.bubbleWebView);
        bubbleWebLoading = windowView.findViewById(R.id.bubbleWebLoading);

        // Header Drag
        headerBar.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = windowParams.x;
                        initialY = windowParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        windowParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        windowParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(windowView, windowParams);
                        return true;
                }
                return false;
            }
        });

        btnBubblePresets.setOnClickListener(v -> showPresetsDialog());

        btnBubbleScreenshot.setOnClickListener(v -> takeScreenshotAndInject());

        btnBubbleResize.setOnClickListener(v -> {
            isLargeWindow = !isLargeWindow;
            DisplayMetrics d = getResources().getDisplayMetrics();
            if (isLargeWindow) {
                windowParams.width = Math.min(d.widthPixels - dpToPx(16), dpToPx(420));
                windowParams.height = dpToPx(600);
            } else {
                windowParams.width = Math.min(d.widthPixels - dpToPx(24), dpToPx(380));
                windowParams.height = dpToPx(480);
            }
            windowManager.updateViewLayout(windowView, windowParams);
        });

        btnBubbleMinimize.setOnClickListener(v -> collapseWindow());

        // Setup WebView with Compact Zoom 80%
        setupWebView();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        bubbleWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings settings = bubbleWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        
        // Compact Zoom: 80% text scale so components fit comfortably
        settings.setTextZoom(80);
        settings.setUseWideViewPort(false);
        settings.setLoadWithOverviewMode(false);

        bubbleWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                bubbleWebLoading.setVisibility(View.GONE);
                injectCompactStyleAndHelpers(view);
                injectAssistantNotifier(view);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    handler.postDelayed(() -> view.loadUrl(DSH_URL), 1500);
                }
            }
        });

        bubbleWebView.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void notifyAgentReply(String messagePreview) {
                showAgentReplyNotification(messagePreview);
            }
        }, "AndroidBridge");
        bubbleWebView.setWebChromeClient(new WebChromeClient());
        bubbleWebView.loadUrl(DSH_URL);
    }

    private void injectCompactStyleAndHelpers(WebView view) {
        String js = "javascript:(function() {" +
                "  try {" +
                "    var style = document.getElementById('dsh-bubble-compact-style');" +
                "    if (!style) {" +
                "      style = document.createElement('style');" +
                "      style.id = 'dsh-bubble-compact-style';" +
                "      style.innerHTML = '" +
                "        body { zoom: 0.85 !important; } " +
                "        [class*=\"popup\"], [class*=\"menu\"], [class*=\"select\"] { max-width: 90vw !important; } " +
                "      ';" +
                "      document.head.appendChild(style);" +
                "    }" +
                "    window.injectDshAttachment = function(filename) {" +
                "      var textarea = document.querySelector('textarea') || document.querySelector('[contenteditable=\"true\"]');" +
                "      if (textarea) {" +
                "        var textToInsert = '@' + filename + ' ';" +
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

    private void expandWindow() {
        if (!isWindowExpanded) {
            isWindowExpanded = true;
            bubbleView.setVisibility(View.GONE);
            
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int expandedWidth = Math.min(dm.widthPixels - dpToPx(24), dpToPx(380));
            int expandedHeight = dpToPx(480);
            
            windowParams.width = expandedWidth;
            windowParams.height = expandedHeight;
            windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
            try {
                windowManager.updateViewLayout(windowView, windowParams);
            } catch (Exception ignored) {}
            windowView.setVisibility(View.VISIBLE);
        }
    }

    private void collapseWindow() {
        if (isWindowExpanded) {
            isWindowExpanded = false;
            windowView.setVisibility(View.INVISIBLE);
            
            windowParams.width = 1;
            windowParams.height = 1;
            windowParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            try {
                windowManager.updateViewLayout(windowView, windowParams);
            } catch (Exception ignored) {}
            bubbleView.setVisibility(View.VISIBLE);
        }
    }

    private void takeScreenshotAndInject() {
        // 1. Sembunyikan window floating sebentar
        windowView.setVisibility(View.INVISIBLE);
        Toast.makeText(this, "📸 Mengambil screenshot...", Toast.LENGTH_SHORT).show();

        // 2. Beri jeda 200ms agar OS merender layar bersih di belakangnya
        handler.postDelayed(() -> new Thread(() -> {
            try {
                File dir = new File("/sdcard/DSH_Screenshots");
                if (!dir.exists()) dir.mkdirs();

                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String filename = "screenshot_" + timeStamp + ".png";
                File screenshotFile = new File(dir, filename);

                // Ambil screenshot via root dan salin juga ke root sdcard
                runRootCommand("screencap -p " + screenshotFile.getAbsolutePath() + " && cp " + screenshotFile.getAbsolutePath() + " /sdcard/current_screen.png 2>/dev/null || true");

                // 3. Tampilkan kembali jendela dan sisipkan @nama_file ke input chat DSH
                handler.post(() -> {
                    windowView.setVisibility(View.VISIBLE);
                    bubbleWebView.evaluateJavascript("javascript:if(window.injectDshAttachment) { window.injectDshAttachment('" + filename + "'); }", null);
                    Toast.makeText(this, "📸 Screenshot @" + filename + " disisipkan ke chat!", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                handler.post(() -> windowView.setVisibility(View.VISIBLE));
            }
        }).start(), 200);
    }

    private void showPresetsDialog() {
        String[] items = new String[]{
                (isGameBoostOn ? "🟢 [ON] 🎮 Game Boost Mode" : "⚪ [OFF] 🎮 Game Boost Mode"),
                (isBatterySaverOn ? "🟢 [ON] 🔋 Extreme Battery Saver" : "⚪ [OFF] 🔋 Extreme Battery Saver"),
                "⚡ [RUN] 🧹 Deep Clean RAM & Cache",
                (isAirplaneOn ? "🟢 [ON] ✈️ Airplane Mode" : "⚪ [OFF] ✈️ Airplane Mode"),
                "⚡ [RUN] 📡 Toggle WiFi Radio"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚡ Presets & Quick Mode");
        builder.setItems(items, (dialog, which) -> {
            if (which == 0) {
                isGameBoostOn = !isGameBoostOn;
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean("PRESET_GAME_BOOST", isGameBoostOn).apply();
                if (isGameBoostOn) {
                    runRootCommand("echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null; sync; echo 3 > /proc/sys/vm/drop_caches");
                    Toast.makeText(this, "🎮 Game Boost [ON]", Toast.LENGTH_SHORT).show();
                } else {
                    runRootCommand("echo schedutil > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null");
                    Toast.makeText(this, "🎮 Game Boost [OFF]", Toast.LENGTH_SHORT).show();
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
        });
        builder.setNegativeButton("Tutup", null);

        AlertDialog dialog = builder.create();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        } else {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_PHONE);
        }
        dialog.show();
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

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) {
                stopSelf();
                return START_NOT_STICKY;
            } else if (ACTION_TOGGLE.equals(action)) {
                if (isWindowExpanded) {
                    collapseWindow();
                } else {
                    expandWindow();
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (bubbleView != null) {
            try { windowManager.removeView(bubbleView); } catch (Exception ignored) {}
        }
        if (windowView != null) {
            try { windowManager.removeView(windowView); } catch (Exception ignored) {}
        }
        if (bubbleWebView != null) {
            bubbleWebView.destroy();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void injectAssistantNotifier(WebView view) {
        try (java.io.InputStream in = getAssets().open("assistant_notifier.js");
             java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            view.evaluateJavascript(sb.toString(), null);
        } catch (Exception ignored) {}
    }

    private static final String BUBBLE_REPLY_CHANNEL_ID = "DSH_AGENT_REPLY_CHANNEL";

    public void showAgentReplyNotification(String replyText) {
        if (replyText == null || replyText.trim().isEmpty()) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            NotificationChannel channel = new NotificationChannel(
                    BUBBLE_REPLY_CHANNEL_ID,
                    "DSH Assistant Replies",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifikasi balasan pesan selesai dari DeepSeek Harness Agent");
            channel.enableVibration(true);
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        androidx.core.app.Person agentPerson = new androidx.core.app.Person.Builder()
                .setName("DeepSeek Agent")
                .setKey("DSH_AGENT")
                .setBot(true)
                .build();

        NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle(agentPerson)
                .setConversationTitle("DeepSeek Harness")
                .addMessage(replyText, System.currentTimeMillis(), agentPerson);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, BUBBLE_REPLY_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_deepseek)
                .setContentTitle("DeepSeek Agent Selesai Menjawab")
                .setContentText(replyText)
                .setStyle(messagingStyle)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(Notification.DEFAULT_ALL);

        if (nm != null) {
            nm.notify(1001, builder.build());
        }
    }

}
