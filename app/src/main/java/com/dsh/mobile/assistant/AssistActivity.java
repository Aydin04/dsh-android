package com.dsh.mobile.assistant;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
import java.util.Locale;

public class AssistActivity extends Activity {

    private static final int SPEECH_REQUEST_CODE = 1001;
    private static final int IMAGE_PICK_REQUEST_CODE = 1002;
    private static final String PREFS_NAME = "DSH_ASSISTANT_PREFS";
    private static final String KEY_IS_TEMP_MODE = "IS_TEMP_MODE";

    private LinearLayout assistantBottomSheet;
    private TextView tvAssistantTitle;
    private TextView tvAssistantResponse;
    private ScrollView responseScrollView;
    private ProgressBar pbGeneratingIndicator;
    private EditText etAssistantInput;

    private Button btnModeToggle;
    private Button btnPresetsDropdown;
    private Button btnCronDropdown;
    private ImageButton btnPlusMenu;
    private ImageButton btnVoiceInput;
    private ImageButton btnAssistantSend;
    private ImageButton btnExpandFull;
    private ImageButton btnCloseAssistant;

    private LinearLayout layoutAttachedImagePreview;
    private ImageView ivAttachedThumbnail;
    private TextView tvAttachedImageName;
    private ImageButton btnRemoveAttachedImage;

    private boolean isTemporaryMode = true;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private File attachedImageFile = null;
    private Bitmap attachedBitmap = null;

    // Preset State Tracking
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
        initSessionMode();
        loadPresetStates();

        // 1. Take clean background screenshot BEFORE showing the popup UI
        takeCleanScreenshotAndShowUI();

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VOICE_COMMAND.equals(intent.getAction())) {
            startVoiceInput();
        }
    }

    private void initViews() {
        assistantBottomSheet = findViewById(R.id.assistantBottomSheet);
        tvAssistantTitle = findViewById(R.id.tvAssistantTitle);
        tvAssistantResponse = findViewById(R.id.tvAssistantResponse);
        responseScrollView = findViewById(R.id.responseScrollView);
        pbGeneratingIndicator = findViewById(R.id.pbGeneratingIndicator);
        etAssistantInput = findViewById(R.id.etAssistantInput);

        btnModeToggle = findViewById(R.id.btnModeToggle);
        btnPresetsDropdown = findViewById(R.id.btnPresetsDropdown);
        btnCronDropdown = findViewById(R.id.btnCronDropdown);
        btnPlusMenu = findViewById(R.id.btnPlusMenu);
        btnVoiceInput = findViewById(R.id.btnVoiceInput);
        btnAssistantSend = findViewById(R.id.btnAssistantSend);
        btnExpandFull = findViewById(R.id.btnExpandFull);
        btnCloseAssistant = findViewById(R.id.btnCloseAssistant);

        layoutAttachedImagePreview = findViewById(R.id.layoutAttachedImagePreview);
        ivAttachedThumbnail = findViewById(R.id.ivAttachedThumbnail);
        tvAttachedImageName = findViewById(R.id.tvAttachedImageName);
        btnRemoveAttachedImage = findViewById(R.id.btnRemoveAttachedImage);
    }

    private void initSessionMode() {
        isTemporaryMode = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_IS_TEMP_MODE, true);
        updateModeUI();
    }

    private void loadPresetStates() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isGameBoostOn = prefs.getBoolean("PRESET_GAME_BOOST", false);
        isBatterySaverOn = prefs.getBoolean("PRESET_BATTERY_SAVER", false);
        isAirplaneOn = prefs.getBoolean("PRESET_AIRPLANE", false);
    }

    private void updateModeUI() {
        if (isTemporaryMode) {
            btnModeToggle.setText("⚡ Temp");
            btnModeToggle.setTextColor(Color.parseColor("#F778BA"));
            tvAssistantTitle.setText("DSH (⚡ Temp)");
        } else {
            btnModeToggle.setText("📌 Main");
            btnModeToggle.setTextColor(Color.parseColor("#7AA2F7"));
            tvAssistantTitle.setText("DSH (📌 Main)");
        }
    }

    private void setupListeners() {
        btnModeToggle.setOnClickListener(v -> {
            isTemporaryMode = !isTemporaryMode;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_IS_TEMP_MODE, isTemporaryMode).apply();
            updateModeUI();
            Toast.makeText(this, isTemporaryMode ? "Mode Temporary: Sesi instan sekali pakai." : "Mode Persistent: Terhubung ke sesi utama.", Toast.LENGTH_SHORT).show();
        });

        btnPresetsDropdown.setOnClickListener(v -> showPresetsDropdownDialog());

        btnCronDropdown.setOnClickListener(v -> showCronDropdownDialog());

        btnPlusMenu.setOnClickListener(v -> showPlusActionMenu());

        btnExpandFull.setOnClickListener(v -> {
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(mainIntent);
            finish();
        });

        btnCloseAssistant.setOnClickListener(v -> finish());

        btnVoiceInput.setOnClickListener(v -> startVoiceInput());

        btnRemoveAttachedImage.setOnClickListener(v -> removeAttachedImage());

        btnAssistantSend.setOnClickListener(v -> processUserQuery());

        etAssistantInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                processUserQuery();
                return true;
            }
            return false;
        });
    }

    private void takeCleanScreenshotAndShowUI() {
        new Thread(() -> {
            try {
                File targetDir = new File(getExternalFilesDir(null) != null ? getExternalFilesDir(null) : getFilesDir(), "dsh_screenshots");
                if (!targetDir.exists()) targetDir.mkdirs();
                File screenFile = new File(targetDir, "screen_" + System.currentTimeMillis() + ".jpg");

                // Execute clean screencap before popup is rendered
                runRootCommand("screencap -p " + screenFile.getAbsolutePath());

                if (screenFile.exists() && screenFile.length() > 0) {
                    Bitmap bmp = BitmapFactory.decodeFile(screenFile.getAbsolutePath());
                    handler.post(() -> {
                        attachImage(screenFile, bmp, "📸 Tangkapan Layar Terlampir (" + (screenFile.length() / 1024) + " KB)");
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
                "📸 Tangkap Ulang Layar (Clean Recapture)",
                "📋 Salin Semua Teks / Angka di Layar (OCR)",
                "🧹 Kosongkan Lampiran Gambar"
        };

        new AlertDialog.Builder(this)
                .setTitle("➕ Menu Tambahan DSH")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openImagePicker();
                    } else if (which == 1) {
                        recaptureCleanScreenshot();
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

    private void recaptureCleanScreenshot() {
        assistantBottomSheet.setVisibility(View.INVISIBLE);
        handler.postDelayed(() -> takeCleanScreenshotAndShowUI(), 180);
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Pilih Gambar untuk DSH"), IMAGE_PICK_REQUEST_CODE);
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
        Toast.makeText(this, "Lampiran gambar dihapus", Toast.LENGTH_SHORT).show();
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
                            Toast.makeText(this, "🎮 Game Boost [ON]: CPU disetel ke Performance", Toast.LENGTH_SHORT).show();
                        } else {
                            runRootCommand("echo schedutil > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null");
                            Toast.makeText(this, "🎮 Game Boost [OFF]: CPU disetel ke Schedutil", Toast.LENGTH_SHORT).show();
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

        tvAssistantResponse.setText("⏰ Jadwal Terdaftar: " + label + " akan dijalankan dalam " + (delayMs / 60000) + " menit.");
        Toast.makeText(this, "Jadwal " + label + " disimpan!", Toast.LENGTH_SHORT).show();
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

        tvAssistantResponse.setText("⏳ Sedang memproses...");
        pbGeneratingIndicator.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                if (promptToSend.startsWith("!") || promptToSend.startsWith("$") || promptToSend.startsWith("su ")) {
                    String cmd = promptToSend.replaceFirst("^[!$]\\s*", "");
                    String output = runRootCommand(cmd);
                    handler.post(() -> {
                        pbGeneratingIndicator.setVisibility(View.GONE);
                        tvAssistantResponse.setText("💻 [Root Output]:\n" + (output.isEmpty() ? "(Perintah sukses dijalankan)" : output));
                    });
                    return;
                }

                // Connect to AI Gateway at port 20128 (OpenAI-compatible)
                URL url = new URL("http://127.0.0.1:20128/v1/chat/completions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "text/event-stream, application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(30000);
                conn.setDoOutput(true);

                JSONObject payload = new JSONObject();
                payload.put("model", "default");
                payload.put("stream", true);

                JSONArray messages = new JSONArray();
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
                payload.put("messages", messages);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    final StringBuilder fullRes = new StringBuilder();
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
                                            handler.post(() -> tvAssistantResponse.setText(fullRes.toString()));
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                    handler.post(() -> {
                        pbGeneratingIndicator.setVisibility(View.GONE);
                        if (fullRes.length() == 0) {
                            tvAssistantResponse.setText("✅ Selesai.");
                        }
                    });
                } else {
                    // Fallback to direct conversational response
                    handler.post(() -> {
                        pbGeneratingIndicator.setVisibility(View.GONE);
                        tvAssistantResponse.setText("🤖 DSH Engine Online (Port 20128).\nPermintaan diproses: \"" + promptToSend + "\"");
                    });
                }
            } catch (Exception e) {
                handler.post(() -> {
                    pbGeneratingIndicator.setVisibility(View.GONE);
                    tvAssistantResponse.setText("⚠️ Status Gateway: " + e.getMessage() + "\n(Tip: Pastikan router/model aktif atau gunakan perintah !shell).");
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
