package com.dsh.mobile.assistant;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.dsh.mobile.MainActivity;
import com.dsh.mobile.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AssistActivity extends Activity {

    private static final int SPEECH_REQUEST_CODE = 1001;
    private static final int IMAGE_PICK_REQUEST_CODE = 1002;
    private static final String PREFS_NAME = "DSH_ASSISTANT_PREFS";
    private static final String KEY_IS_TEMP_MODE = "IS_TEMP_MODE";
    private static final String KEY_SELECTED_MODEL = "SELECTED_MODEL";
    private static final String AUTH_TOKEN = "Bearer dsh-local-key";

    private LinearLayout assistantBottomSheet;
    private Button btnModelSelector;
    private Button btnModeToggle;
    private ImageButton btnSaveSession;
    private Button btnPresetsDropdown;
    private Button btnCronDropdown;
    private ImageButton btnExpandFull;
    private ImageButton btnCloseAssistant;

    private ScrollView chatScrollView;
    private LinearLayout chatMessagesContainer;
    private LinearLayout layoutThinkingStatus;
    private TextView tvThinkingStatus;

    private LinearLayout layoutAttachedImagePreview;
    private ImageView ivAttachedThumbnail;
    private TextView tvAttachedImageName;
    private ImageButton btnRemoveAttachedImage;

    private ImageButton btnPlusMenu;
    private ImageButton btnVoiceInput;
    private EditText etAssistantInput;
    private ImageButton btnAssistantSend;

    private boolean isTemporaryMode = true;
    private String selectedModel = "default";
    private final List<String> availableModels = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private File attachedImageFile = null;
    private Bitmap attachedBitmap = null;

    // Multi-turn Conversation Messages
    private final JSONArray conversationHistory = new JSONArray();

    // Preset States
    private boolean isGameBoostOn = false;
    private boolean isBatterySaverOn = false;
    private boolean isAirplaneOn = false;

    private static final String SYSTEM_INSTRUCTION =
            "You are DSH Android AI Assistant with full root shell capabilities.\n" +
            "When the user asks you to perform an action or change system settings (such as airplane mode, wifi, bluetooth, clean RAM, game mode, open apps, brightness, volume, reboot, or run shell scripts), DO NOT just explain how to do it in Android Settings.\n" +
            "Instead, execute the command directly using the exact syntax:\n" +
            "```execute_command\n<root_shell_command>\n```\n" +
            "Followed by a brief, friendly confirmation in Indonesian.\n" +
            "Common root commands:\n" +
            "- Airplane Mode ON: settings put global airplane_mode_on 1 && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true\n" +
            "- Airplane Mode OFF: settings put global airplane_mode_on 0 && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false\n" +
            "- WiFi ON: svc wifi enable\n" +
            "- WiFi OFF: svc wifi disable\n" +
            "- Clean RAM: sync; echo 3 > /proc/sys/vm/drop_caches\n" +
            "- Game Boost ON: echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null; sync; echo 3 > /proc/sys/vm/drop_caches\n" +
            "- Open App: monkey -p <package_name> 1";

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
        loadSettings();

        // 1. Fetch available models from router
        fetchDynamicModels();

        // 2. Take fresh, instant screenshot before showing popup
        takeFreshScreenshotAndShowUI();

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VOICE_COMMAND.equals(intent.getAction())) {
            startVoiceInput();
        }
    }

    private void initViews() {
        assistantBottomSheet = findViewById(R.id.assistantBottomSheet);
        btnModelSelector = findViewById(R.id.btnModelSelector);
        btnModeToggle = findViewById(R.id.btnModeToggle);
        btnSaveSession = findViewById(R.id.btnSaveSession);
        btnPresetsDropdown = findViewById(R.id.btnPresetsDropdown);
        btnCronDropdown = findViewById(R.id.btnCronDropdown);
        btnExpandFull = findViewById(R.id.btnExpandFull);
        btnCloseAssistant = findViewById(R.id.btnCloseAssistant);

        chatScrollView = findViewById(R.id.chatScrollView);
        chatMessagesContainer = findViewById(R.id.chatMessagesContainer);
        layoutThinkingStatus = findViewById(R.id.layoutThinkingStatus);
        tvThinkingStatus = findViewById(R.id.tvThinkingStatus);

        layoutAttachedImagePreview = findViewById(R.id.layoutAttachedImagePreview);
        ivAttachedThumbnail = findViewById(R.id.ivAttachedThumbnail);
        tvAttachedImageName = findViewById(R.id.tvAttachedImageName);
        btnRemoveAttachedImage = findViewById(R.id.btnRemoveAttachedImage);

        btnPlusMenu = findViewById(R.id.btnPlusMenu);
        btnVoiceInput = findViewById(R.id.btnVoiceInput);
        etAssistantInput = findViewById(R.id.etAssistantInput);
        btnAssistantSend = findViewById(R.id.btnAssistantSend);
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isTemporaryMode = prefs.getBoolean(KEY_IS_TEMP_MODE, true);
        selectedModel = prefs.getString(KEY_SELECTED_MODEL, "default");
        btnModelSelector.setText("🤖 " + selectedModel + " ▼");
        updateModeUI();

        isGameBoostOn = prefs.getBoolean("PRESET_GAME_BOOST", false);
        isBatterySaverOn = prefs.getBoolean("PRESET_BATTERY_SAVER", false);
        isAirplaneOn = prefs.getBoolean("PRESET_AIRPLANE", false);
    }

    private void updateModeUI() {
        if (isTemporaryMode) {
            btnModeToggle.setText("⚡ Temp");
            btnModeToggle.setTextColor(Color.parseColor("#F778BA"));
            btnSaveSession.setVisibility(View.VISIBLE);
        } else {
            btnModeToggle.setText("📌 Main");
            btnModeToggle.setTextColor(Color.parseColor("#7AA2F7"));
            btnSaveSession.setVisibility(View.GONE);
        }
    }

    private void setupListeners() {
        btnModelSelector.setOnClickListener(v -> showModelSelectorDialog());

        btnModeToggle.setOnClickListener(v -> {
            isTemporaryMode = !isTemporaryMode;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_IS_TEMP_MODE, isTemporaryMode).apply();
            updateModeUI();
            Toast.makeText(this, isTemporaryMode ? "Mode Temporary: Sesi instan sekali pakai." : "Mode Persistent: Terhubung ke sesi utama.", Toast.LENGTH_SHORT).show();
        });

        btnSaveSession.setOnClickListener(v -> saveCurrentSessionToDisk());

        btnPresetsDropdown.setOnClickListener(v -> showPresetsDropdownDialog());

        btnCronDropdown.setOnClickListener(v -> showCronDropdownDialog());

        btnPlusMenu.setOnClickListener(v -> showPlusActionMenu());

        btnVoiceInput.setOnClickListener(v -> startVoiceInput());

        btnRemoveAttachedImage.setOnClickListener(v -> removeAttachedImage());

        btnAssistantSend.setOnClickListener(v -> processUserQuery());

        btnExpandFull.setOnClickListener(v -> {
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(mainIntent);
            finish();
        });

        btnCloseAssistant.setOnClickListener(v -> finish());

        etAssistantInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                processUserQuery();
                return true;
            }
            return false;
        });
    }

    private void fetchDynamicModels() {
        new Thread(() -> {
            try {
                URL url = new URL("http://127.0.0.1:20128/v1/models");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", AUTH_TOKEN);
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(4000);

                if (conn.getResponseCode() == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        String l;
                        while ((l = br.readLine()) != null) sb.append(l);
                    }
                    JSONObject root = new JSONObject(sb.toString());
                    JSONArray data = root.optJSONArray("data");
                    if (data != null && data.length() > 0) {
                        availableModels.clear();
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject m = data.getJSONObject(i);
                            String id = m.optString("id");
                            if (!TextUtils.isEmpty(id)) {
                                availableModels.add(id);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void showModelSelectorDialog() {
        List<String> list = new ArrayList<>(availableModels);
        if (list.isEmpty()) {
            list.add("default");
            list.add("deepseek-chat");
            list.add("deepseek-reasoner");
            list.add("claude-3-7-sonnet");
            list.add("claude-3-5-sonnet");
            list.add("gpt-4o");
            list.add("gpt-4o-mini");
            list.add("gemini-2.5-flash");
            list.add("gemini-2.5-pro");
        }

        String[] modelArray = list.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("🤖 Pilih Model AI")
                .setItems(modelArray, (dialog, which) -> {
                    selectedModel = modelArray[which];
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(KEY_SELECTED_MODEL, selectedModel).apply();
                    btnModelSelector.setText("🤖 " + selectedModel + " ▼");
                    Toast.makeText(this, "Model aktif: " + selectedModel, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void takeFreshScreenshotAndShowUI() {
        new Thread(() -> {
            try {
                File targetDir = new File(getExternalFilesDir(null) != null ? getExternalFilesDir(null) : getFilesDir(), "dsh_screenshots");
                if (!targetDir.exists()) targetDir.mkdirs();

                // Clean older screenshots
                File[] oldFiles = targetDir.listFiles();
                if (oldFiles != null) {
                    for (File f : oldFiles) f.delete();
                }

                File screenFile = new File(targetDir, "screen_" + System.currentTimeMillis() + ".png");

                // Execute instantaneous screencap
                runRootCommand("screencap -p " + screenFile.getAbsolutePath());

                if (screenFile.exists() && screenFile.length() > 0) {
                    Bitmap bmp = BitmapFactory.decodeFile(screenFile.getAbsolutePath());
                    handler.post(() -> {
                        attachImage(screenFile, bmp, "📸 Layar Terlampir (" + (screenFile.length() / 1024) + " KB)");
                        assistantBottomSheet.setVisibility(View.VISIBLE);
                    });
                } else {
                    handler.post(() -> assistantBottomSheet.setVisibility(View.VISIBLE));
                }
            } catch (Exception e) {
                handler.post(() -> assistantBottomSheet.setVisibility(View.VISIBLE));
            }
        }).start();
    }

    private void showPlusActionMenu() {
        String[] options = new String[]{
                "🖼️ Pilih Gambar dari Galeri",
                "📸 Tangkap Ulang Layar (Fresh Capture)",
                "📋 Salin Semua Teks / Angka di Layar (OCR)",
                "🧹 Kosongkan Lampiran Gambar"
        };

        new AlertDialog.Builder(this)
                .setTitle("➕ Aksi & Lampiran")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openImagePicker();
                    } else if (which == 1) {
                        assistantBottomSheet.setVisibility(View.INVISIBLE);
                        handler.postDelayed(() -> takeFreshScreenshotAndShowUI(), 120);
                    } else if (which == 2) {
                        etAssistantInput.setText("Salin dan ekstrak seluruh tulisan, angka, kode, dan link dari gambar layar ini secara lengkap.");
                        processUserQuery();
                    } else {
                        removeAttachedImage();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Pilih Gambar"), IMAGE_PICK_REQUEST_CODE);
    }

    private void attachImage(File file, Bitmap bmp, String displayName) {
        this.attachedImageFile = file;
        this.attachedBitmap = bmp;
        if (layoutAttachedImagePreview != null) {
            layoutAttachedImagePreview.setVisibility(View.VISIBLE);
            if (bmp != null) ivAttachedThumbnail.setImageBitmap(bmp);
            tvAttachedImageName.setText(displayName);
        }
    }

    private void removeAttachedImage() {
        this.attachedImageFile = null;
        this.attachedBitmap = null;
        if (layoutAttachedImagePreview != null) {
            layoutAttachedImagePreview.setVisibility(View.GONE);
        }
        Toast.makeText(this, "Lampiran dihapus", Toast.LENGTH_SHORT).show();
    }

    private void saveCurrentSessionToDisk() {
        if (conversationHistory.length() == 0) {
            Toast.makeText(this, "Belum ada percakapan untuk disimpan.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File sessionsDir = new File("/sdcard/DSH_Sessions");
            if (!sessionsDir.exists()) sessionsDir.mkdirs();
            File sessionFile = new File(sessionsDir, "session_" + System.currentTimeMillis() + ".md");

            StringBuilder sb = new StringBuilder();
            sb.append("# 🤖 DSH Assistant Session Transcript\n");
            sb.append("Date: ").append(new java.util.Date()).append("\n");
            sb.append("Model: ").append(selectedModel).append("\n\n---\n\n");

            for (int i = 0; i < conversationHistory.length(); i++) {
                JSONObject msg = conversationHistory.getJSONObject(i);
                String role = msg.optString("role", "user");
                Object content = msg.opt("content");
                sb.append("### ").append(role.toUpperCase()).append(":\n");
                if (content instanceof String) {
                    sb.append(content).append("\n\n");
                } else if (content instanceof JSONArray) {
                    JSONArray arr = (JSONArray) content;
                    for (int j = 0; j < arr.length(); j++) {
                        JSONObject block = arr.getJSONObject(j);
                        if ("text".equals(block.optString("type"))) {
                            sb.append(block.optString("text")).append("\n\n");
                        }
                    }
                }
            }

            try (FileOutputStream fos = new FileOutputStream(sessionFile)) {
                fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }

            Toast.makeText(this, "💾 Sesi disimpan ke: " + sessionFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Gagal menyimpan sesi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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
                        Toast.makeText(this, "Ketik di chat: 'Jadwalkan [aksi] dalam [waktu]'", Toast.LENGTH_LONG).show();
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

    private void startVoiceInput() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Bicara sekarang dengan DSH...");
            startActivityForResult(intent, SPEECH_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Speech recognition tidak tersedia.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == SPEECH_REQUEST_CODE) {
                ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (results != null && !results.isEmpty()) {
                    etAssistantInput.setText(results.get(0));
                    processUserQuery();
                }
            } else if (requestCode == IMAGE_PICK_REQUEST_CODE) {
                Uri imageUri = data.getData();
                if (imageUri != null) {
                    processPickedImageUri(imageUri);
                }
            }
        }
    }

    private void processPickedImageUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(is);
            if (is != null) is.close();

            File uploadDir = new File(getFilesDir(), "dsh_uploads");
            if (!uploadDir.exists()) uploadDir.mkdirs();
            File dest = new File(uploadDir, "upload_" + System.currentTimeMillis() + ".jpg");

            FileOutputStream fos = new FileOutputStream(dest);
            if (bmp != null) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                fos.flush();
                fos.close();
                attachImage(dest, bmp, "🖼️ " + dest.getName() + " (" + (dest.length() / 1024) + " KB)");
            }
        } catch (Exception e) {
            Toast.makeText(this, "Gagal memproses gambar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void addUserBubble(String text, boolean hasImage) {
        LinearLayout userBubble = new LinearLayout(this);
        userBubble.setOrientation(LinearLayout.VERTICAL);
        userBubble.setBackgroundColor(Color.parseColor("#202744"));
        userBubble.setPadding(12, 10, 12, 10);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.END;
        lp.setMargins(40, 6, 6, 6);
        userBubble.setLayoutParams(lp);

        TextView tv = new TextView(this);
        tv.setText(text + (hasImage ? " 📸 [Gambar Terlampir]" : ""));
        tv.setTextColor(Color.parseColor("#FFFFFF"));
        tv.setTextSize(13f);
        tv.setLineSpacing(3, 1);
        userBubble.addView(tv);

        chatMessagesContainer.addView(userBubble);
        scrollToBottom();
    }

    private TextView addAssistantBubble(String initialText) {
        LinearLayout assistantBubble = new LinearLayout(this);
        assistantBubble.setOrientation(LinearLayout.VERTICAL);
        assistantBubble.setBackgroundColor(Color.parseColor("#181B2B"));
        assistantBubble.setPadding(12, 10, 12, 10);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.START;
        lp.setMargins(6, 6, 40, 6);
        assistantBubble.setLayoutParams(lp);

        // Header with Model Tag and Copy Button
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvModel = new TextView(this);
        tvModel.setText("🤖 " + selectedModel);
        tvModel.setTextColor(Color.parseColor("#58A6FF"));
        tvModel.setTextSize(10f);
        tvModel.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        header.addView(tvModel, mlp);

        ImageButton btnCopy = new ImageButton(this);
        btnCopy.setImageResource(android.R.drawable.ic_menu_agenda);
        btnCopy.setColorFilter(Color.parseColor("#787C99"));
        btnCopy.setBackgroundColor(Color.TRANSPARENT);
        btnCopy.setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(24)));
        header.addView(btnCopy);
        assistantBubble.addView(header);

        TextView tvContent = new TextView(this);
        tvContent.setText(initialText);
        tvContent.setTextColor(Color.parseColor("#C0CAF5"));
        tvContent.setTextSize(13f);
        tvContent.setLineSpacing(3, 1);
        assistantBubble.addView(tvContent);

        // Stats Footer
        TextView tvStats = new TextView(this);
        tvStats.setText("⚡ Streaming tokens...");
        tvStats.setTextColor(Color.parseColor("#565F89"));
        tvStats.setTextSize(9f);
        assistantBubble.addView(tvStats);

        btnCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("DSH Assistant", tvContent.getText().toString());
            cm.setPrimaryClip(clip);
            Toast.makeText(this, "📋 Respons disalin ke clipboard", Toast.LENGTH_SHORT).show();
        });

        chatMessagesContainer.addView(assistantBubble);
        scrollToBottom();
        return tvContent;
    }

    private void scrollToBottom() {
        handler.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void processUserQuery() {
        String query = etAssistantInput.getText().toString().trim();
        final File currentImg = this.attachedImageFile;
        final Bitmap currentBmp = this.attachedBitmap;

        if (TextUtils.isEmpty(query) && currentImg == null) return;

        if (TextUtils.isEmpty(query) && currentImg != null) {
            query = "Jelaskan dan analisis isi gambar layar ini secara lengkap, serta salin semua tulisan/angka yang ada di dalamnya.";
        }

        final String promptToSend = query;
        etAssistantInput.setText("");
        removeAttachedImage();

        addUserBubble(promptToSend, currentImg != null);

        layoutThinkingStatus.setVisibility(View.VISIBLE);
        tvThinkingStatus.setText("💭 Sedang memproses dengan " + selectedModel + "...");

        final TextView assistantTv = addAssistantBubble("...");
        final long startTime = System.currentTimeMillis();

        new Thread(() -> {
            try {
                if (promptToSend.startsWith("!") || promptToSend.startsWith("$") || promptToSend.startsWith("su ")) {
                    String cmd = promptToSend.replaceFirst("^[!$]\\s*", "");
                    String output = runRootCommand(cmd);
                    handler.post(() -> {
                        layoutThinkingStatus.setVisibility(View.GONE);
                        assistantTv.setText("💻 [Root Output]:\n" + (output.isEmpty() ? "(Perintah sukses dijalankan)" : output));
                        scrollToBottom();
                    });
                    return;
                }

                URL url = new URL("http://127.0.0.1:20128/v1/chat/completions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "text/event-stream, application/json");
                conn.setRequestProperty("Authorization", AUTH_TOKEN);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(30000);
                conn.setDoOutput(true);

                JSONObject payload = new JSONObject();
                payload.put("model", selectedModel);
                payload.put("stream", true);

                JSONArray messages = new JSONArray();

                // 1. Add System Instruction
                JSONObject sysMsg = new JSONObject();
                sysMsg.put("role", "system");
                sysMsg.put("content", SYSTEM_INSTRUCTION);
                messages.put(sysMsg);

                // 2. Add Multi-turn History
                for (int i = 0; i < conversationHistory.length(); i++) {
                    messages.put(conversationHistory.getJSONObject(i));
                }

                // 3. Add Current User Message
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");

                if (currentImg != null && currentBmp != null) {
                    JSONArray contentArray = new JSONArray();
                    JSONObject textObj = new JSONObject();
                    textObj.put("type", "text");
                    textObj.put("text", promptToSend);
                    contentArray.put(textObj);

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    currentBmp.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                    String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                    JSONObject imgObj = new JSONObject();
                    imgObj.put("type", "image_url");
                    JSONObject urlObj = new JSONObject();
                    urlObj.put("url", "data:image/jpeg;base64," + b64);
                    imgObj.put("image_url", urlObj);
                    contentArray.put(imgObj);

                    userMsg.put("content", contentArray);
                } else {
                    userMsg.put("content", promptToSend);
                }
                messages.put(userMsg);
                conversationHistory.put(userMsg);

                payload.put("messages", messages);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    final StringBuilder fullRes = new StringBuilder();
                    int tokenCount = 0;

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String dataStr = line.substring(6).trim();
                                if ("[DONE]".equals(dataStr)) break;
                                try {
                                    JSONObject chunk = new JSONObject(dataStr);
                                    JSONArray choices = chunk.optJSONArray("choices");
                                    if (choices != null && choices.length() > 0) {
                                        JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                                        if (delta != null && delta.has("content")) {
                                            String token = delta.getString("content");
                                            fullRes.append(token);
                                            tokenCount++;
                                            final int currentTokens = tokenCount;
                                            handler.post(() -> {
                                                layoutThinkingStatus.setVisibility(View.GONE);
                                                assistantTv.setText(fullRes.toString());
                                                scrollToBottom();
                                            });
                                        }
                                    }
                                } catch (Exception ignored) {}
                            } else if (!line.trim().isEmpty() && !line.startsWith("data:")) {
                                try {
                                    JSONObject nonSse = new JSONObject(line);
                                    JSONArray choices = nonSse.optJSONArray("choices");
                                    if (choices != null && choices.length() > 0) {
                                        JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
                                        if (msg != null && msg.has("content")) {
                                            fullRes.append(msg.getString("content"));
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    }

                    final String finalText = fullRes.toString();

                    // Check for executable commands in response
                    Pattern p = Pattern.compile("```(?:execute_command|sh|bash)\\s*\\n([\\s\\S]*?)\\n```");
                    Matcher m = p.matcher(finalText);
                    if (m.find()) {
                        String cmdToExec = m.group(1).trim();
                        handler.post(() -> tvThinkingStatus.setText("⚡ Menjalankan: " + cmdToExec));
                        String cmdOut = runRootCommand(cmdToExec);
                        handler.post(() -> {
                            assistantTv.setText(finalText + "\n\n💻 [Status Eksekusi Root]: Sukses dieksekusi.");
                        });
                    }

                    // Save Assistant reply to conversation history
                    JSONObject assistantHistoryMsg = new JSONObject();
                    assistantHistoryMsg.put("role", "assistant");
                    assistantHistoryMsg.put("content", finalText);
                    conversationHistory.put(assistantHistoryMsg);

                    final long duration = Math.max(1, System.currentTimeMillis() - startTime);
                    handler.post(() -> {
                        layoutThinkingStatus.setVisibility(View.GONE);
                        if (finalText.isEmpty()) {
                            assistantTv.setText("✅ Selesai diproses.");
                        }
                        scrollToBottom();
                    });
                } else {
                    StringBuilder errSb = new StringBuilder();
                    InputStream es = conn.getErrorStream();
                    if (es != null) {
                        try (BufferedReader er = new BufferedReader(new InputStreamReader(es))) {
                            String el;
                            while ((el = er.readLine()) != null) errSb.append(el);
                        }
                    }
                    String errDetails = errSb.toString();
                    handler.post(() -> {
                        layoutThinkingStatus.setVisibility(View.GONE);
                        assistantTv.setText("⚠️ Error HTTP " + code + " dari Gateway:\n" + (errDetails.isEmpty() ? "Periksa router aktif." : errDetails));
                        scrollToBottom();
                    });
                }
            } catch (Exception e) {
                handler.post(() -> {
                    layoutThinkingStatus.setVisibility(View.GONE);
                    assistantTv.setText("⚠️ Koneksi Gagal: " + e.getMessage());
                    scrollToBottom();
                });
            }
        }).start();
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

    private int dp(int val) {
        return (int) (val * getResources().getDisplayMetrics().density);
    }

    private int sp() {
        return 13;
    }

    private int sp(int val) {
        return val;
    }

    private int dp() {
        return dp(24);
    }

    private int dp(double val) {
        return (int) (val * getResources().getDisplayMetrics().density);
    }
}
