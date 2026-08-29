package com.aydin.dsh;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class LocalEngineService extends Service {

    private static final String CHANNEL_ID = "DSH_LOCAL_ENGINE";
    private Process nodeProcess;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        new Thread(this::extractAndRunEngine).start();
    }

    private void extractAndRunEngine() {
        try {
            File binDir = new File(getFilesDir(), "bin");
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DSH Local Engine Active")
                .setContentText("Autonomous agent execution running in background")
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
