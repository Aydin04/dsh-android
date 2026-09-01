package com.dsh.mobile.assistant;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

public class AssistantAlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String command = intent.getStringExtra("COMMAND");
        String label = intent.getStringExtra("LABEL");

        if (command == null || command.isEmpty()) return;

        new Thread(() -> {
            try {
                Process p = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(p.getOutputStream());
                os.writeBytes(command + "\n");
                os.writeBytes("exit\n");
                os.flush();
                p.waitFor();

                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(context, "⏰ DSH Scheduler: " + (label != null ? label : "Task") + " selesai dieksekusi!", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(context, "⚠️ DSH Scheduler Gagal: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
}
