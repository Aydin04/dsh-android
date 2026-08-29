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

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LocalEngineService extends Service {

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

            File nodeFile = new File(binDir, "node");
            if (!nodeFile.exists() || nodeFile.length() == 0) {
                try (InputStream in = getAssets().open("engine/node");
                     OutputStream out = new FileOutputStream(nodeFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
                nodeFile.setExecutable(true);
            }

            File dshDir = new File(filesDir, "dsh");
            File dshBin = new File(dshDir, "lib/bin.js");

            if (!dshBin.exists()) {
                File tarFile = new File(filesDir, "dsh-core.tar.gz");
                try (InputStream in = getAssets().open("engine/dsh-core.tar.gz");
                     OutputStream out = new FileOutputStream(tarFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }

                // Untar dsh package
                ProcessBuilder tarPb = new ProcessBuilder("tar", "-xzf", tarFile.getAbsolutePath(), "-C", filesDir.getAbsolutePath());
                Process tarProc = tarPb.start();
                tarProc.waitFor();
                tarFile.delete();
            }

            // Launch On-Device DSH Server
            String homeDir = Environment.getExternalStorageDirectory().getAbsolutePath();
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

            // Poll port 3000 readiness
            for (int i = 0; i < 40; i++) {
                try {
                    URL url = new URL("http://127.0.0.1:3000/");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(1000);
                    conn.setReadTimeout(1000);
                    int code = conn.getResponseCode();
                    if (code == 200) {
                        isEngineReady = true;
                        sendBroadcast(new Intent("com.aydin.dsh.ENGINE_READY"));
                        break;
                    }
                } catch (Exception ignored) {
                }
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            e.printStackTrace();
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
