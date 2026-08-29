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
            if (!binDir.exists()) binDir.mkdirs();

            // 1. Resolve Native Executable Node.js Binary (from nativeLibraryDir)
            String nativeLibDir = getApplicationInfo().nativeLibraryDir;
            File nodeLib = new File(nativeLibDir, "libnode.so");
            File nodeFile = new File(binDir, "node");

            String nodeExecutablePath;
            if (nodeLib.exists() && nodeLib.canExecute()) {
                emitLog("[NATIVE] Found executable Node.js at nativeLibraryDir: " + nodeLib.getAbsolutePath());
                nodeExecutablePath = nodeLib.getAbsolutePath();
            } else if (nodeLib.exists()) {
                emitLog("[NATIVE] Found libnode.so at nativeLibraryDir: " + nodeLib.getAbsolutePath());
                nodeExecutablePath = nodeLib.getAbsolutePath();
            } else {
                emitLog("[WARN] libnode.so not in nativeLibraryDir (" + nativeLibDir + "), falling back to files/bin/node");
                nodeExecutablePath = nodeFile.getAbsolutePath();
            }

            // 2. Extract DSH Core via pure Java Tar
            File dshDir = new File(filesDir, "dsh");
            File dshBin = new File(dshDir, "lib/bin.js");

            if (!dshBin.exists()) {
                emitLog("[EXTRACT] Extracting DeepSeek Harness packages from engine/dsh-core.tar...");
                try (InputStream rawIn = getAssets().open("engine/dsh-core.tar");
                     TarArchiveInputStream tarIn = new TarArchiveInputStream(rawIn)) {

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
                        if (count % 1000 == 0) {
                            emitLog("[EXTRACT] Unpacked " + count + " files...");
                        }
                    }
                    emitLog("[EXTRACT] Extracted total " + count + " DSH package files successfully.");
                }
            } else {
                emitLog("[EXTRACT] DSH packages cached at " + dshDir.getAbsolutePath());
            }

            // 3. Test executing node --version
            emitLog("[TEST] Testing Node execution: " + nodeExecutablePath + " -v");
            try {
                Process testProc = new ProcessBuilder(nodeExecutablePath, "-v").start();
                BufferedReader r = new BufferedReader(new InputStreamReader(testProc.getInputStream()));
                String version = r.readLine();
                testProc.waitFor();
                emitLog("[TEST SUCCESS] Node.js verified on Android! Version: " + version);
            } catch (Exception err) {
                emitLog("[TEST ERROR] Node.js test failed: " + err.getMessage());
            }

            // 4. Launch On-Device DSH Server
            emitLog("[SERVER] Launching dsh --profile web --port 3000 ...");
            ProcessBuilder pb = new ProcessBuilder(
                    nodeExecutablePath,
                    dshBin.getAbsolutePath(),
                    "--profile", "web",
                    "--no-open",
                    "--port", "3000"
            );
            pb.directory(filesDir);
            pb.environment().put("HOME", filesDir.getAbsolutePath());
            pb.environment().put("NODE_PATH", new File(dshDir, "node_modules").getAbsolutePath());
            pb.environment().put("PATH", nativeLibDir + ":" + binDir.getAbsolutePath() + ":/system/bin:/system/xbin");
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

            // 5. Poll port 3000 readiness
            for (int i = 1; i <= 60; i++) {
                try {
                    URL url = new URL("http://127.0.0.1:3000/");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(1000);
                    conn.setReadTimeout(1000);
                    int code = conn.getResponseCode();
                    if (code == 200) {
                        emitLog("[READY] HTTP 200 OK received! DeepSeek Harness dashboard ready.");
                        isEngineReady = true;
                        sendBroadcast(new Intent(ACTION_READY));
                        break;
                    }
                } catch (Exception ignored) {
                }
                if (i % 5 == 0) {
                    emitLog("[WAIT] Waiting for server on http://127.0.0.1:3000/ (" + i + "s)...");
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
