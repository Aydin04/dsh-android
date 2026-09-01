package com.dsh.mobile.assistant;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
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
import java.io.FileInputStream;
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

    private TextView tvAssistantTitle;
    private TextView tvAssistantResponse;
    private ScrollView responseScrollView;
    private EditText etAssistantInput;
    private Button btnModeToggle;
    private ImageButton btnVoiceInput;
    private ImageButton btnAttachImage;
    private ImageButton btnAssistantSend;
    private ImageButton btnExpandFull;
    private ImageButton btnCloseAssistant;

    private LinearLayout layoutAttachedImagePreview;
    private ImageView ivAttachedThumbnail;
    private TextView tvAttachedImageName;
    private ImageButton btnRemoveAttachedImage;

    private Button pillScreenshot;
    private Button pillPickImage;
    private Button pillPresetGame;
    private Button pillSchedule;
    private Button pillCleanRam;
    private Button pillToggleWifi;
    private Button pillAirplane;

    private boolean isTemporaryMode = true;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String currentTemporarySessionId = null;
    private File attachedImageFile = null;
    private Bitmap attachedBitmap = null;

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

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VOICE_COMMAND.equals(intent.getAction())) {
            startVoiceInput();
        }
    }

    private void initViews() {
        tvAssistantTitle = findViewById(R.id.tvAssistantTitle);
        tvAssistantResponse = findViewById(R.id.tvAssistantResponse);
        responseScrollView = findViewById(R.id.responseScrollView);
        etAssistantInput = findViewById(R.id.etAssistantInput);
        btnModeToggle = findViewById(R.id.btnModeToggle);
        btnVoiceInput = findViewById(R.id.btnVoiceInput);
        btnAttachImage = findViewById(R.id.btnAttachImage);
        btnAssistantSend = findViewById(R.id.btnAssistantSend);
        btnExpandFull = findViewById(R.id.btnExpandFull);
        btnCloseAssistant = findViewById(R.id.btnCloseAssistant);

        layoutAttachedImagePreview = findViewById(R.id.layoutAttachedImagePreview);
        ivAttachedThumbnail = findViewById(R.id.ivAttachedThumbnail);
        tvAttachedImageName = findViewById(R.id.tvAttachedImageName);
        btnRemoveAttachedImage = findViewById(R.id.btnRemoveAttachedImage);

        pillScreenshot = findViewById(R.id.pillScreenshot);
        pillPickImage = findViewById(R.id.pillPickImage);
        pillPresetGame = findViewById(R.id.pillPresetGame);
        pillSchedule = findViewById(R.id.pillSchedule);
        pillCleanRam = findViewById(R.id.pillCleanRam);
        pillToggleWifi = findViewById(R.id.pillToggleWifi);
        pillAirplane = findViewById(R.id.pillAirplane);
    }

    private void initSessionMode() {
        isTemporaryMode = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_IS_TEMP_MODE, true);
        updateModeUI();
        if (isTemporaryMode) {
            currentTemporarySessionId = "ephemeral-" + System.currentTimeMillis();
        }
    }

    private void updateModeUI() {
        if (isTemporaryMode) {
            btnModeToggle.setText("⚡ Temp");
            btnModeToggle.setTextColor(Color.parseColor("#F778BA"));
            tvAssistantTitle.setText("🤖 DSH Assistant (⚡ Temp)");
        } else {
            btnModeToggle.setText("📌 Main");
            btnModeToggle.setTextColor(Color.parseColor("#7AA2F7"));
            tvAssistantTitle.setText("🤖 DSH Assistant (📌 Main)");
        }
    }

    private void setupListeners() {
        btnModeToggle.setOnClickListener(v -> {
            isTemporaryMode = !isTemporaryMode;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_IS_TEMP_MODE, isTemporaryMode).apply();
            updateModeUI();
            if (isTemporaryMode) {
                currentTemporarySessionId = "ephemeral-" + System.currentTimeMillis();
                Toast.makeText(this, "Mode Temporary: Percakapan tidak disimpan ke riwayat permanen.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Mode Persistent: Terhubung ke sesi utama DSH.", Toast.LENGTH_SHORT).show();
            }
        });

        btnExpandFull.setOnClickListener(v -> {
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(mainIntent);
            finish();
        });

        btnCloseAssistant.setOnClickListener(v -> finish());

        btnVoiceInput.setOnClickListener(v -> startVoiceInput());

        btnAttachImage.setOnClickListener(v -> openImagePicker());

        pillPickImage.setOnClickListener(v -> openImagePicker());

        pillScreenshot.setOnClickListener(v -> captureScreenAndAttach());

        btnRemoveAttachedImage.setOnClickListener(v -> removeAttachedImage());

        btnAssistantSend.setOnClickListener(v -> processUserQuery());

        etAssistantInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                processUserQuery();
                return true;
            }
            return false;
        });

        pillPresetGame.setOnClickListener(v -> executeQuickRootAction(
                "🎮 Game Boost",
                "echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null || true; sync; echo 3 > /proc/sys/vm/drop_caches",
                "Preset Game Boost diaktifkan: CPU Governor disetel ke performance & RAM dioptimalkan."
        ));

        pillCleanRam.setOnClickListener(v -> executeQuickRootAction(
                "🧹 Clean RAM",
                "sync; echo 3 > /proc/sys/vm/drop_caches",
                "RAM dibersihkan! Cache memori sistem berhasil dikosongkan."
        ));

        pillToggleWifi.setOnClickListener(v -> executeQuickRootAction(
                "📡 Toggle WiFi",
                "svc wifi disable 2>/dev/null || svc wifi enable 2>/dev/null",
                "Perintah peralihan WiFi telah dieksekusi via root."
        ));

        pillAirplane.setOnClickListener(v -> executeQuickRootAction(
                "✈️ Airplane Mode",
                "settings put global airplane_mode_on 1 && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true 2>/dev/null || true",
                "Mode pesawat diaktifkan via root."
        ));

        pillSchedule.setOnClickListener(v -> showQuickScheduleDialog());
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Pilih Gambar untuk Dikirim ke DSH"), IMAGE_PICK_REQUEST_CODE);
    }

    private void captureScreenAndAttach() {
        Toast.makeText(this, "Mengambil tangkapan layar...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            File targetDir = new File(getExternalFilesDir(null) != null ? getExternalFilesDir(null) : getFilesDir(), "dsh_screenshots");
            if (!targetDir.exists()) targetDir.mkdirs();
            File screenFile = new File(targetDir, "screen_" + System.currentTimeMillis() + ".png");

            String cmd = "screencap -p " + screenFile.getAbsolutePath();
            runRootCommand(cmd);

            if (screenFile.exists() && screenFile.length() > 0) {
                Bitmap bmp = BitmapFactory.decodeFile(screenFile.getAbsolutePath());
                handler.post(() -> attachImage(screenFile, bmp, "📸 Screenshot Layar (" + (screenFile.length() / 1024) + " KB)"));
            } else {
                handler.post(() -> Toast.makeText(this, "Gagal mengambil screenshot via root.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void attachImage(File file, Bitmap bmp, String displayName) {
        this.attachedImageFile = file;
        this.attachedBitmap = bmp;
        if (layoutAttachedImagePreview != null) {
            layoutAttachedImagePreview.setVisibility(View.VISIBLE);
            if (bmp != null) ivAttachedThumbnail.setImageBitmap(bmp);
            tvAttachedImageName.setText(displayName);
            Toast.makeText(this, "Gambar berhasil dilampirkan!", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeAttachedImage() {
        this.attachedImageFile = null;
        this.attachedBitmap = null;
        if (layoutAttachedImagePreview != null) {
            layoutAttachedImagePreview.setVisibility(View.GONE);
        }
    }

    private void startVoiceInput() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Bicara sekarang dengan DSH Assistant...");
            startActivityForResult(intent, SPEECH_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Speech recognition tidak tersedia di perangkat ini.", Toast.LENGTH_SHORT).show();
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
            query = "Jelaskan dan analisis gambar terlampir ini secara lengkap, serta salin teks/angka yang ada di dalamnya.";
        }

        final String promptToSend = query;
        etAssistantInput.setText("");
        removeAttachedImage();

        tvAssistantResponse.setText("⏳ Sedang memproses: \"" + promptToSend + "\"" + (currentImg != null ? " [Dengan Lampiran Gambar]" : "") + "...");

        new Thread(() -> {
            try {
                if (promptToSend.startsWith("!") || promptToSend.startsWith("$") || promptToSend.startsWith("su ")) {
                    String cmd = promptToSend.replaceFirst("^[!$]\\s*", "");
                    String output = runRootCommand(cmd);
                    handler.post(() -> tvAssistantResponse.setText("💻 [Root Output]:\n" + (output.isEmpty() ? "(Perintah sukses dijalankan)" : output)));
                    return;
                }

                // Prepare request body with image support
                URL url = new URL("http://127.0.0.1:3080/api/chat");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(20000);
                conn.setDoOutput(true);

                JSONObject payload = new JSONObject();
                payload.put("prompt", promptToSend);
                if (isTemporaryMode) {
                    payload.put("sessionId", currentTemporarySessionId);
                    payload.put("ephemeral", true);
                }

                if (currentImg != null && currentBmp != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    currentBmp.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                    String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                    payload.put("image", "data:image/jpeg;base64," + b64);
                    payload.put("imagePath", currentImg.getAbsolutePath());
                }

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    StringBuilder responseSb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            responseSb.append(line).append("\n");
                        }
                    }
                    String rawRes = responseSb.toString();
                    handler.post(() -> tvAssistantResponse.setText(rawRes));
                } else {
                    handler.post(() -> tvAssistantResponse.setText("🤖 DSH Engine Online (Port 3080).\nPesan terkirim ke agent: \"" + promptToSend + "\"" + (currentImg != null ? "\n[File: " + currentImg.getName() + "]" : "")));
                }
            } catch (Exception e) {
                handler.post(() -> tvAssistantResponse.setText("⚠️ Respons: " + e.getMessage() + "\n(Gunakan !perintah untuk eksekusi langsung perintah root)."));
            }
        }).start();
    }

    private void executeQuickRootAction(String title, String shellCmd, String successMsg) {
        tvAssistantResponse.setText("⏳ Menjalankan " + title + "...");
        new Thread(() -> {
            String out = runRootCommand(shellCmd);
            handler.post(() -> {
                tvAssistantResponse.setText("✅ " + title + " Sukses!\n" + successMsg + (out.isEmpty() ? "" : "\n\nOutput: " + out));
                Toast.makeText(this, title + " Selesai", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void showQuickScheduleDialog() {
        String[] options = new String[]{
                "✈️ Mode Pesawat 10 Menit Lagi",
                "🧹 Clean RAM Setiap 1 Jam",
                "🌙 Mode Hemat Baterai (Malam)",
                "⏰ Buat Alarm / Jadwal Kustom"
        };

        new android.app.AlertDialog.Builder(this)
                .setTitle("⏰ DSH Assistant Scheduler")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        scheduleRootTask(10 * 60 * 1000, "settings put global airplane_mode_on 1", "Mode Pesawat (10m)");
                    } else if (which == 1) {
                        scheduleRootTask(60 * 60 * 1000, "sync; echo 3 > /proc/sys/vm/drop_caches", "Clean RAM (1 Jam)");
                    } else if (which == 2) {
                        scheduleRootTask(5 * 60 * 1000, "settings put system screen_brightness 10", "Dim Layar (5m)");
                    } else {
                        Toast.makeText(this, "Jadwalkan kustom langsung dengan mengetik di chat: 'Jadwalkan [tugas] dalam [waktu]'", Toast.LENGTH_LONG).show();
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
        if (isTemporaryMode && currentTemporarySessionId != null) {
            currentTemporarySessionId = null;
        }
        super.onDestroy();
    }
}
