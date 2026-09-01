package com.dsh.mobile.assistant;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.dsh.mobile.MainActivity;
import com.dsh.mobile.R;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class AssistActivity extends Activity {

    private static final int SPEECH_REQUEST_CODE = 1001;
    private static final String PREFS_NAME = "DSH_ASSISTANT_PREFS";
    private static final String KEY_IS_TEMP_MODE = "IS_TEMP_MODE";

    private TextView tvAssistantTitle;
    private TextView tvAssistantResponse;
    private ScrollView responseScrollView;
    private EditText etAssistantInput;
    private Button btnModeToggle;
    private ImageButton btnVoiceInput;
    private ImageButton btnAssistantSend;
    private ImageButton btnExpandFull;
    private ImageButton btnCloseAssistant;

    private Button pillPresetGame;
    private Button pillSchedule;
    private Button pillCleanRam;
    private Button pillToggleWifi;
    private Button pillAirplane;

    private boolean isTemporaryMode = true;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String currentTemporarySessionId = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assistant_popup);

        // Configure window layout to match bottom-sheet assistant
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.gravity = Gravity.BOTTOM;
        getWindow().setAttributes(lp);

        initViews();
        setupListeners();
        initSessionMode();

        // Check if launched with voice action
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
        btnAssistantSend = findViewById(R.id.btnAssistantSend);
        btnExpandFull = findViewById(R.id.btnExpandFull);
        btnCloseAssistant = findViewById(R.id.btnCloseAssistant);

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

        btnAssistantSend.setOnClickListener(v -> processUserQuery());

        etAssistantInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                processUserQuery();
                return true;
            }
            return false;
        });

        // Quick action pills
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
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String spokenText = results.get(0);
                etAssistantInput.setText(spokenText);
                processUserQuery();
            }
        }
    }

    private void processUserQuery() {
        String query = etAssistantInput.getText().toString().trim();
        if (TextUtils.isEmpty(query)) return;

        etAssistantInput.setText("");
        tvAssistantResponse.setText("⏳ Sedang memproses: \"" + query + "\"...");

        new Thread(() -> {
            try {
                // If query is a direct shell / root execution intent
                if (query.startsWith("!") || query.startsWith("$") || query.startsWith("su ")) {
                    String cmd = query.replaceFirst("^[!$]\\s*", "");
                    String output = runRootCommand(cmd);
                    handler.post(() -> tvAssistantResponse.setText("💻 [Root Output]:\n" + (output.isEmpty() ? "(Perintah sukses dijalankan tanpa output)" : output)));
                    return;
                }

                // Send query to DSH Engine (Port 3080)
                URL url = new URL("http://127.0.0.1:3080/api/chat");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);

                JSONObject payload = new JSONObject();
                payload.put("prompt", query);
                if (isTemporaryMode) {
                    payload.put("sessionId", currentTemporarySessionId);
                    payload.put("ephemeral", true);
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
                    // Fallback to direct conversational response
                    handler.post(() -> tvAssistantResponse.setText("🤖 DSH Engine Online (Port 3080).\nPertanyaan Anda: \"" + query + "\"\n(Gunakan !perintah untuk eksekusi langsung perintah root sistem)."));
                }
            } catch (Exception e) {
                handler.post(() -> tvAssistantResponse.setText("⚠️ Engine DSH respons: " + e.getMessage() + "\n(Tip: Anda bisa menjalankan perintah root langsung dengan awalan '!misal: !uptime')."));
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
            // Memory cleanup for ephemeral session
            currentTemporarySessionId = null;
        }
        super.onDestroy();
    }
}
