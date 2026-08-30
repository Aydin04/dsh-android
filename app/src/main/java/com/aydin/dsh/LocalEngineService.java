package com.aydin.dsh;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class LocalEngineService extends Service {

    public static final String ACTION_LOG = "com.aydin.dsh.ENGINE_LOG";
    public static final String ACTION_READY = "com.aydin.dsh.ENGINE_READY";
    public static final String EXTRA_MESSAGE = "EXTRA_MESSAGE";

    private static final String TAG = "DSH_LOCAL_ENGINE";
    private static final String CHANNEL_ID = "DSH_LOCAL_ENGINE";
    public static boolean isEngineReady = false;
    private Process nodeProcess;

    private void emitLog(String msg) {
        Log.i(TAG, msg);
        Intent intent = new Intent(ACTION_LOG);
        intent.putExtra(EXTRA_MESSAGE, msg);
        sendBroadcast(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        new Thread(this::extractAndRunEngine).start();
    }

    private void extractAndRunEngine() {
        try {
            emitLog("[INIT] Starting Engine preparation on device...");
            File filesDir = getFilesDir();
            File binDir = new File(filesDir, "bin");
            File libDir = new File(filesDir, "lib");
            if (!binDir.exists()) binDir.mkdirs();
            if (!libDir.exists()) libDir.mkdirs();

            // 1. Extract Node.js binary
            File nodeFile = new File(binDir, "node");
            if (!nodeFile.exists() || nodeFile.length() == 0) {
                emitLog("[EXTRACT] Extracting Node.js binary to " + nodeFile.getAbsolutePath() + "...");
                try (InputStream in = getAssets().open("engine/node");
                     OutputStream out = new FileOutputStream(nodeFile)) {
                    byte[] buffer = new byte[32768];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
            }

            nodeFile.setReadable(true, false);
            nodeFile.setExecutable(true, false);
            try {
                Runtime.getRuntime().exec("chmod 755 " + nodeFile.getAbsolutePath()).waitFor();
            } catch (Exception ignored) {
            }

            // 2. Extract compressed native shared libraries (engine-libs.tar.gz / engine-libs.tar)
            File testLib = new File(libDir, "libz.so.1");
            if (!testLib.exists()) {
                String libAsset = "engine/engine-libs.tar.gz";
                boolean isGzip = true;
                try {
                    String[] engineAssets = getAssets().list("engine");
                    if (engineAssets != null) {
                        List<String> assetList = Arrays.asList(engineAssets);
                        if (assetList.contains("engine-libs.tar.gz")) {
                            libAsset = "engine/engine-libs.tar.gz";
                            isGzip = true;
                        } else if (assetList.contains("engine-libs.tar")) {
                            libAsset = "engine/engine-libs.tar";
                            isGzip = false;
                        }
                    }
                } catch (Exception ignored) {}

                emitLog("[EXTRACT] Extracting Node.js native shared libraries from " + libAsset + "...");
                try (InputStream rawIn = getAssets().open(libAsset)) {
                    InputStream inStream = isGzip ? new GzipCompressorInputStream(rawIn) : rawIn;
                    try (TarArchiveInputStream tarIn = new TarArchiveInputStream(inStream)) {
                        TarArchiveEntry entry;
                        int libCount = 0;
                        while ((entry = tarIn.getNextTarEntry()) != null) {
                            File outputFile = new File(libDir, entry.getName());
                            if (entry.isDirectory()) {
                                if (!outputFile.exists()) outputFile.mkdirs();
                            } else {
                                File parent = outputFile.getParentFile();
                                if (parent != null && !parent.exists()) parent.mkdirs();
                                try (OutputStream out = new FileOutputStream(outputFile)) {
                                    byte[] buf = new byte[32768];
                                    int len;
                                    while ((len = tarIn.read(buf)) != -1) {
                                        out.write(buf, 0, len);
                                    }
                                }
                            }
                            libCount++;
                        }
                        emitLog("[EXTRACT] Extracted " + libCount + " native shared libraries.");
                    }
                } catch (Exception e) {
                    emitLog("[EXTRACT LIBS ERROR] " + e.getMessage());
                }
            }

            // 3. Extract compressed DSH Core packages (dsh-core.tar.gz / dsh-core.tar)
            File dshDir = new File(filesDir, "dsh");
            File dshBin = new File(dshDir, "lib/bin.js");

            if (!dshBin.exists()) {
                String dshAsset = "engine/dsh-core.tar.gz";
                boolean isGzip = true;
                try {
                    String[] engineAssets = getAssets().list("engine");
                    if (engineAssets != null) {
                        List<String> assetList = Arrays.asList(engineAssets);
                        if (assetList.contains("dsh-core.tar.gz")) {
                            dshAsset = "engine/dsh-core.tar.gz";
                            isGzip = true;
                        } else if (assetList.contains("dsh-core.tar")) {
                            dshAsset = "engine/dsh-core.tar";
                            isGzip = false;
                        }
                    }
                } catch (Exception ignored) {}

                emitLog("[EXTRACT] Extracting DeepSeek Harness packages from " + dshAsset + "...");
                try (InputStream rawIn = getAssets().open(dshAsset)) {
                    InputStream inStream = isGzip ? new GzipCompressorInputStream(rawIn) : rawIn;
                    try (TarArchiveInputStream tarIn = new TarArchiveInputStream(inStream)) {
                        TarArchiveEntry entry;
                        int count = 0;
                        while ((entry = tarIn.getNextTarEntry()) != null) {
                            File outputFile = new File(filesDir, entry.getName());
                            if (entry.isDirectory()) {
                                if (!outputFile.exists()) outputFile.mkdirs();
                            } else {
                                File parent = outputFile.getParentFile();
                                if (parent != null && !parent.exists()) parent.mkdirs();

                                try (OutputStream out = new FileOutputStream(outputFile)) {
                                    byte[] buf = new byte[32768];
                                    int len;
                                    while ((len = tarIn.read(buf)) != -1) {
                                        out.write(buf, 0, len);
                                    }
                                }
                            }
                            count++;
                            if (count % 2000 == 0) {
                                emitLog("[EXTRACT] Unpacked " + count + " files...");
                            }
                        }
                        emitLog("[EXTRACT] Extracted total " + count + " DSH package files successfully.");
                    }
                }
            } else {
                emitLog("[EXTRACT] DSH packages cached at " + dshDir.getAbsolutePath());
            }

            // 4. Test executing node --version with RPATH and LD_LIBRARY_PATH
            emitLog("[TEST] Testing Node execution: " + nodeFile.getAbsolutePath() + " -v");
            try {
                ProcessBuilder testPb = new ProcessBuilder(nodeFile.getAbsolutePath(), "-v");
                testPb.environment().put("LD_LIBRARY_PATH", libDir.getAbsolutePath());
                testPb.redirectErrorStream(true);
                Process testProc = testPb.start();

                BufferedReader r = new BufferedReader(new InputStreamReader(testProc.getInputStream()));
                String version = r.readLine();
                testProc.waitFor();
                emitLog("[TEST SUCCESS] Node.js verified! Output: " + version);
            } catch (Exception err) {
                emitLog("[TEST ERROR] Node.js test failed: " + err.getMessage());
            }

            // 5. Launch On-Device DSH Server
            // Set workspace working directory directly to root of internal storage (/sdcard)
            File externalDir = Environment.getExternalStorageDirectory();
            File workspaceDir = (externalDir != null && externalDir.exists()) ? externalDir : new File("/sdcard");

            emitLog("[SERVER] Launching dsh --profile web --port 3080 ...");
            emitLog("[SERVER] Primary Workspace Directory: " + workspaceDir.getAbsolutePath());
            
            // Build rich PATH including standard Android bins, Termux bins, and Magisk/KSU/APatch root su bins
            String enrichedPath = binDir.getAbsolutePath() + 
                    ":/data/adb/ksu/bin" + 
                    ":/data/adb/ap/bin" + 
                    ":/data/adb/magisk" + 
                    ":/sbin" + 
                    ":/system/bin" + 
                    ":/system/xbin" + 
                    ":/data/data/com.termux/files/usr/bin";

            ProcessBuilder pb = new ProcessBuilder(
                    nodeFile.getAbsolutePath(),
                    dshBin.getAbsolutePath(),
                    "--profile", "web",
                    "--no-open",
                    "--port", "3080"
            );
            pb.directory(workspaceDir.exists() ? workspaceDir : filesDir);
            pb.environment().put("HOME", filesDir.getAbsolutePath());
            pb.environment().put("DSH_EXTERNAL_STORAGE", workspaceDir.getAbsolutePath());
            pb.environment().put("LD_LIBRARY_PATH", libDir.getAbsolutePath());
            pb.environment().put("NODE_PATH", new File(dshDir, "node_modules").getAbsolutePath());
            pb.environment().put("PATH", enrichedPath);
            pb.environment().put("DSH_PERMISSION_MODE", "danger-full-access");
            pb.environment().put("TMPDIR", filesDir.getAbsolutePath());
            pb.redirectErrorStream(true);

            nodeProcess = pb.start();
            emitLog("[SERVER] Process spawned PID active. Streaming stdout & stderr:");

            // Stream real-time node logs directly to UI
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(nodeProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        emitLog("[DSH Output] " + line);
                    }
                } catch (Exception e) {
                    emitLog("[SERVER ERROR] Error reading log stream: " + e.getMessage());
                }
            }).start();

            // 6. Poll port 3080 readiness (require 2 consecutive successful pings)
            int consecutiveSuccess = 0;
            for (int i = 1; i <= 60; i++) {
                try {
                    URL url = new URL("http://127.0.0.1:3080/");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(1500);
                    conn.setReadTimeout(1500);
                    int code = conn.getResponseCode();
                    if (code == 200) {
                        consecutiveSuccess++;
                        if (consecutiveSuccess >= 2) {
                            emitLog("[READY] HTTP 200 OK confirmed! DeepSeek Harness dashboard ready.");
                            isEngineReady = true;
                            sendBroadcast(new Intent(ACTION_READY));
                            break;
                        }
                    } else {
                        consecutiveSuccess = 0;
                    }
                } catch (Exception e) {
                    consecutiveSuccess = 0;
                }
                if (i % 5 == 0) {
                    emitLog("[WAIT] Waiting for server on http://127.0.0.1:3080/ (" + i + "s)...");
                }
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            emitLog("[FATAL EXCEPTION] " + e.toString());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DSH Engine Running On-Device")
                .setContentText("Local DeepSeek Harness server active on port 3000")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();

        startForeground(1, notification);
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "DSH Local Background Engine",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        if (nodeProcess != null) {
            nodeProcess.destroy();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
