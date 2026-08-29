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

public class LocalEngineService extends Service {

    private static final String TAG = "DSH_LOCAL_ENGINE";
    private static final String CHANNEL_ID = "DSH_LOCAL_ENGINE";
    public static boolean isEngineReady = false;
    private Process nodeProcess;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        new Thread(this::extractAndRunEngine).start();
    }

    private void extractAndRunEngine() {
        try {
            File filesDir = getFilesDir();
            File binDir = new File(filesDir, "bin");
            if (!binDir.exists()) binDir.mkdirs();

            // 1. Extract Node.js binary
            File nodeFile = new File(binDir, "node");
            if (!nodeFile.exists() || nodeFile.length() == 0) {
                Log.d(TAG, "Extracting Node.js binary...");
                try (InputStream in = getAssets().open("engine/node");
                     OutputStream out = new FileOutputStream(nodeFile)) {
                    byte[] buffer = new byte[16384];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
                nodeFile.setReadable(true, false);
                nodeFile.setExecutable(true, false);
            }

            // 2. Extract DSH Core via pure Java TarArchiveInputStream (no system tar needed)
            File dshDir = new File(filesDir, "dsh");
            File dshBin = new File(dshDir, "lib/bin.js");

            if (!dshBin.exists()) {
                Log.d(TAG, "Extracting DSH package using pure Java Tar...");
                try (InputStream rawIn = getAssets().open("engine/dsh-core.tar.gz");
                     GzipCompressorInputStream gzIn = new GzipCompressorInputStream(rawIn);
                     TarArchiveInputStream tarIn = new TarArchiveInputStream(gzIn)) {
                    
                    TarArchiveEntry entry;
                    while ((entry = tarIn.getNextTarEntry()) != null) {
                        File outputFile = new File(filesDir, entry.getName());
                        if (entry.isDirectory()) {
                            if (!outputFile.exists()) outputFile.mkdirs();
                        } else {
                            File parent = outputFile.getParentFile();
                            if (parent != null && !parent.exists()) parent.mkdirs();
                            
                            try (OutputStream out = new FileOutputStream(outputFile)) {
                                byte[] buf = new byte[16384];
                                int len;
                                while ((len = tarIn.read(buf)) != -1) {
                                    out.write(buf, 0, len);
                                }
                            }
                        }
                    }
                }
                Log.d(TAG, "DSH extraction completed successfully.");
            }

            // 3. Launch On-Device DSH Server
            Log.d(TAG, "Starting DSH Node server on 127.0.0.1:3000...");
            ProcessBuilder pb = new ProcessBuilder(
                    nodeFile.getAbsolutePath(),
                    dshBin.getAbsolutePath(),
                    "--profile", "web",
                    "--no-open",
                    "--port", "3000"
            );
            pb.directory(filesDir);
            pb.environment().put("HOME", filesDir.getAbsolutePath());
            pb.environment().put("NODE_PATH", new File(dshDir, "node_modules").getAbsolutePath());
            pb.environment().put("PATH", binDir.getAbsolutePath() + ":/system/bin:/system/xbin");
            pb.redirectErrorStream(true);

            nodeProcess = pb.start();

            // Stream logs
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(nodeProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Log.i(TAG, "[DSH Node] " + line);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error reading DSH log", e);
                }
            }).start();

            // 4. Poll port 3000 readiness
            for (int i = 0; i < 60; i++) {
                try {
                    URL url = new URL("http://127.0.0.1:3000/");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(1000);
                    conn.setReadTimeout(1000);
                    int code = conn.getResponseCode();
                    if (code == 200) {
                        Log.d(TAG, "DSH Server is READY!");
                        isEngineReady = true;
                        sendBroadcast(new Intent("com.aydin.dsh.ENGINE_READY"));
                        break;
                    }
                } catch (Exception ignored) {
                }
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed in extractAndRunEngine", e);
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
