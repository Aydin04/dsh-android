package com.dsh.mobile;

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
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LocalEngineService extends Service {

    public static final String ACTION_LOG = "com.dsh.mobile.ENGINE_LOG";
    public static final String ACTION_READY = "com.dsh.mobile.ENGINE_READY";
    public static final String EXTRA_MESSAGE = "EXTRA_MESSAGE";

    private static final String TAG = "DSH_LOCAL_ENGINE";
    private static final String CHANNEL_ID = "DSH_LOCAL_ENGINE";
    public static boolean isEngineReady = false;
    private Process nodeProcess;
    private Process atomicProcess;

    private void emitLog(String msg) {
        Log.i(TAG, msg);
        Intent intent = new Intent(ACTION_LOG);
        intent.putExtra(EXTRA_MESSAGE, msg);
        sendBroadcast(intent);
    }

    private void emitReady() {
        isEngineReady = true;
        sendBroadcast(new Intent(ACTION_READY));
    }


    private static final String REPLY_CHANNEL_ID = "DSH_AGENT_REPLY_CHANNEL";
    private final android.content.BroadcastReceiver replyReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.dsh.mobile.NOTIFY_REPLY".equals(intent.getAction())) {
                String reply = intent.getStringExtra("reply");
                if (reply != null && !reply.trim().isEmpty()) {
                    showAgentReplyNotification(reply);
                }
            }
        }
    };

    public void showAgentReplyNotification(String replyText) {
        if (replyText == null) return;
        replyText = replyText.replaceFirst("(?i)^(ASSISTANT|USER|AGENT|DEEPSEEK)\\s*[:\\n]*", "").trim();
        if (replyText.isEmpty()) return;

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            NotificationChannel chan = new NotificationChannel(REPLY_CHANNEL_ID, "DeepSeek Agent Replies", NotificationManager.IMPORTANCE_HIGH);
            chan.setDescription("Notifikasi balasan AI DeepSeek Harness");
            chan.enableVibration(true);
            chan.setVibrationPattern(new long[]{0, 200, 100, 200});
            chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(chan);
        }

        String preview = replyText.replaceAll("[#*`_>~]", "").replaceAll("\\s+", " ").trim();
        if (preview.length() > 140) {
            preview = preview.substring(0, 137) + "...";
        }

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        android.app.PendingIntent pi = android.app.PendingIntent.getActivity(
                this, 1005, openIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? android.app.PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, REPLY_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_deepseek)
                .setContentTitle("🤖 DeepSeek Agent")
                .setContentText(preview)
                .setSubText("Balasan Selesai")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(replyText))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pi);

        if (nm != null) {
            nm.notify(1005, builder.build());
        }
    }

    private android.os.PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        try {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "DSH::EngineWakeLock");
                wakeLock.acquire();
            }
        } catch (Exception ignored) {}

        try {
            android.content.IntentFilter filter = new android.content.IntentFilter("com.dsh.mobile.NOTIFY_REPLY");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(replyReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(replyReceiver, filter);
            }
        } catch (Exception ignored) {}

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

            // Check if app was updated to force fresh extraction of core packages
            int currentVersionCode = 14;
            try {
                currentVersionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            } catch (Exception ignored) {}

            android.content.SharedPreferences prefs = getSharedPreferences("DSH_ENGINE_STATE", Context.MODE_PRIVATE);
            int lastExtractedVersion = prefs.getInt("EXTRACTED_VERSION", 0);
            boolean isNewAppVersion = (currentVersionCode != lastExtractedVersion);
            if (isNewAppVersion) {
                emitLog("[UPDATE] Detected new app version (" + currentVersionCode + "). Updating engine bundles...");
            }

            // Create sandbox-isolated empty openssl.cnf to prevent OpenSSL permission denied on Termux path
            File opensslCnf = new File(filesDir, "openssl.cnf");
            if (!opensslCnf.exists()) {
                try {
                    opensslCnf.createNewFile();
                } catch (Exception ignored) {}
            }

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

            // Extract native ripgrep binary if bundled
            File rgFile = new File(binDir, "rg");
            try {
                String[] engineAssets = getAssets().list("engine");
                if (engineAssets != null) {
                    List<String> assetList = Arrays.asList(engineAssets);
                    if (assetList.contains("rg")) {
                        emitLog("[EXTRACT] Extracting Ripgrep (rg) binary to " + rgFile.getAbsolutePath() + "...");
                        try (InputStream in = getAssets().open("engine/rg");
                             OutputStream out = new FileOutputStream(rgFile)) {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = in.read(buf)) != -1) {
                                out.write(buf, 0, len);
                            }
                        }
                        rgFile.setReadable(true, false);
                        rgFile.setExecutable(true, false);
                        try { Runtime.getRuntime().exec("chmod 755 " + rgFile.getAbsolutePath()).waitFor(); } catch (Exception ignored) {}
                    }

                    // Extract proot binary
                    File prootFile = new File(binDir, "proot");
                    if (assetList.contains("proot")) {
                        emitLog("[EXTRACT] Extracting PRoot binary to " + prootFile.getAbsolutePath() + "...");
                        try (InputStream in = getAssets().open("engine/proot");
                             OutputStream out = new FileOutputStream(prootFile)) {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = in.read(buf)) != -1) {
                                out.write(buf, 0, len);
                            }
                        }
                        prootFile.setReadable(true, false);
                        prootFile.setExecutable(true, false);
                        try { Runtime.getRuntime().exec("chmod 755 " + prootFile.getAbsolutePath()).waitFor(); } catch (Exception ignored) {}
                    }

                    // Extract universal sh / bash shim
                    File shShimFile = new File(binDir, "sh");
                    File bashShimFile = new File(binDir, "bash");
                    try (InputStream in = getAssets().open("sh_shim.sh");
                         OutputStream out = new FileOutputStream(shShimFile)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) != -1) {
                            out.write(buf, 0, len);
                        }
                    }
                    try (InputStream in = getAssets().open("sh_shim.sh");
                         OutputStream out = new FileOutputStream(bashShimFile)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) != -1) {
                            out.write(buf, 0, len);
                        }
                    }
                    bashShimFile.setReadable(true, false);
                    bashShimFile.setExecutable(true, false);
                    try { Runtime.getRuntime().exec("chmod 755 " + bashShimFile.getAbsolutePath()).waitFor(); } catch (Exception ignored) {}

                    // Create universal pnpm wrapper script
                    File pnpmShimFile = new File(binDir, "pnpm");
                    try (FileWriter fw = new FileWriter(pnpmShimFile)) {
                        fw.write("#!/system/bin/sh\n");
                        fw.write("export HOME=\"" + filesDir.getAbsolutePath() + "\"\n");
                        fw.write("export LD_LIBRARY_PATH=\"" + libDir.getAbsolutePath() + "\"\n");
                        File asarLoader = new File(filesDir, "asar-engine/asar-preload.mjs");
                        File dshAsar = new File(filesDir, "engine/dsh.asar");
                        if (dshAsar.exists() && asarLoader.exists()) {
                            fw.write("exec \"" + nodeFile.getAbsolutePath() + "\" --import \"" + asarLoader.getAbsolutePath() + "\" \"" + dshAsar.getAbsolutePath() + "/node_modules/pnpm/bin/pnpm.cjs\" \"$@\"\n");
                        } else {
                            fw.write("exec \"" + nodeFile.getAbsolutePath() + "\" \"" + filesDir.getAbsolutePath() + "/dsh/node_modules/pnpm/bin/pnpm.cjs\" \"$@\"\n");
                        }
                    } catch (Exception ignored) {}
                    pnpmShimFile.setReadable(true, false);
                    pnpmShimFile.setExecutable(true, false);
                    try { Runtime.getRuntime().exec("chmod 755 " + pnpmShimFile.getAbsolutePath()).waitFor(); } catch (Exception ignored) {}

                    // Extract Alpine Rootfs if bundled
                    File rootfsDir = new File(filesDir, "rootfs");
                    if (!rootfsDir.exists() && assetList.contains("alpine-rootfs.tar.gz")) {
                        rootfsDir.mkdirs();
                        emitLog("[EXTRACT] Extracting Alpine Linux Rootfs (apk package manager ready)...");
                        try (InputStream rawIn = getAssets().open("engine/alpine-rootfs.tar.gz");
                             InputStream gzipIn = new GzipCompressorInputStream(rawIn);
                             TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn)) {
                            TarArchiveEntry entry;
                            while ((entry = tarIn.getNextTarEntry()) != null) {
                                File target = new File(rootfsDir, entry.getName());
                                if (entry.isDirectory()) {
                                    target.mkdirs();
                                } else {
                                    File parent = target.getParentFile();
                                    if (parent != null && !parent.exists()) parent.mkdirs();
                                    try (OutputStream out = new FileOutputStream(target)) {
                                        byte[] buf = new byte[16384];
                                        int len;
                                        while ((len = tarIn.read(buf)) != -1) {
                                            out.write(buf, 0, len);
                                        }
                                    }
                                }
                            }
                            emitLog("[EXTRACT SUCCESS] Alpine Linux Rootfs ready!");
                        } catch (Exception e) {
                            emitLog("[ROOTFS EXTRACT ERROR] " + e.getMessage());
                        }
                    }
                }
            } catch (Exception ignored) {}

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
                                long entryModTime = entry.getLastModifiedDate() != null ? entry.getLastModifiedDate().getTime() : 0;
                                if (outputFile.exists() && entry.getSize() > 0 && outputFile.length() == entry.getSize() && (entryModTime <= 0 || Math.abs(outputFile.lastModified() - entryModTime) < 3000)) {
                                    libCount++;
                                    continue;
                                }
                                File parent = outputFile.getParentFile();
                                if (parent != null && !parent.exists()) parent.mkdirs();
                                try (OutputStream out = new FileOutputStream(outputFile)) {
                                    byte[] buf = new byte[32768];
                                    int len;
                                    while ((len = tarIn.read(buf)) != -1) {
                                        out.write(buf, 0, len);
                                    }
                                }
                                if (entry.getLastModifiedDate() != null) {
                                    outputFile.setLastModified(entry.getLastModifiedDate().getTime());
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

            // 3. Prepare ASAR Virtual FS Engine & Shims
            File engineDir = new File(filesDir, "engine");
            if (!engineDir.exists()) engineDir.mkdirs();
            File asarEngineDir = new File(filesDir, "asar-engine");
            if (!asarEngineDir.exists()) asarEngineDir.mkdirs();

            // Extract ASAR Hook Scripts (force update on new app version)
            if (isNewAppVersion) {
                File[] oldScripts = asarEngineDir.listFiles();
                if (oldScripts != null) {
                    for (File os : oldScripts) {
                        if (os.getName().endsWith(".mjs") || os.getName().endsWith(".cjs")) os.delete();
                    }
                }
            }
            copyAssetDir("asar-engine", asarEngineDir);

            // Check if packaged as Single-File ASAR Image
            File dshAsar = new File(engineDir, "dsh.asar");
            File atomicAsar = new File(engineDir, "atomic-router.asar");
            File dshDir = new File(filesDir, "dsh");
            File dshBin = new File(dshDir, "lib/bin.js");
            File atomicDir = new File(filesDir, "atomic-router");

            List<String> assetList = Collections.emptyList();
            try {
                String[] ea = getAssets().list("engine");
                if (ea != null) assetList = Arrays.asList(ea);
            } catch (Exception ignored) {}

            boolean hasDshAsarAsset = assetList.contains("dsh.asar");
            if (hasDshAsarAsset) {
                emitLog("[ASAR ENGINE] Detected Single-File dsh.asar! Copying archive without extracting loose files...");
                copyAssetFile("engine/dsh.asar", dshAsar);
                emitLog("[ASAR ENGINE SUCCESS] dsh.asar ready (" + (dshAsar.length() / (1024 * 1024)) + " MB). Cleaner immune!");
            } else {
                // Fallback extraction of dsh-core.tar.gz for backward compatibility
                File appBootDir = new File(dshDir, "node_modules/@deepseek-ai/dsh-app-boot");
                boolean needsExtraction = !dshBin.exists() || !appBootDir.exists() || isNewAppVersion;
                if (needsExtraction) {
                    emitLog("[EXTRACT] Unpacking dsh-core archive...");
                    String dshAsset = assetList.contains("dsh-core.tar.gz") ? "engine/dsh-core.tar.gz" : "engine/dsh-core.tar";
                    boolean isGzip = dshAsset.endsWith(".gz");
                    try (InputStream rawIn = getAssets().open(dshAsset)) {
                        InputStream inStream = isGzip ? new GzipCompressorInputStream(rawIn) : rawIn;
                        try (TarArchiveInputStream tarIn = new TarArchiveInputStream(inStream)) {
                            TarArchiveEntry entry;
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
                            }
                        }
                    } catch (Exception e) {
                        emitLog("[EXTRACT ERROR] " + e.getMessage());
                    }
                }
            }

            // AtomicRouter ASAR vs TAR
            boolean hasAtomicAsarAsset = assetList.contains("atomic-router.asar");
            if (hasAtomicAsarAsset) {
                emitLog("[ASAR ENGINE] Detected Single-File atomic-router.asar! Copying archive...");
                copyAssetFile("engine/atomic-router.asar", atomicAsar);
                emitLog("[ASAR ENGINE SUCCESS] atomic-router.asar ready (" + (atomicAsar.length() / (1024 * 1024)) + " MB).");
            } else if (assetList.contains("atomic-router.tar.xz") || assetList.contains("atomic-router.tar.gz")) {
                if (!new File(atomicDir, "server.js").exists() || isNewAppVersion) {
                    emitLog("[EXTRACT] Extracting AtomicRouter tar...");
                    String aAsset = assetList.contains("atomic-router.tar.xz") ? "engine/atomic-router.tar.xz" : "engine/atomic-router.tar.gz";
                    boolean isXz = aAsset.endsWith(".xz");
                    try (InputStream rawIn = getAssets().open(aAsset)) {
                        InputStream inStream = isXz ? new org.tukaani.xz.XZInputStream(rawIn) : new GzipCompressorInputStream(rawIn);
                        try (TarArchiveInputStream tarIn = new TarArchiveInputStream(inStream)) {
                            TarArchiveEntry entry;
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
                            }
                        }
                    } catch (Exception err) {
                        emitLog("[ATOMIC EXTRACT ERROR] " + err.getMessage());
                    }
                }
            }

            prefs.edit().putInt("EXTRACTED_VERSION", currentVersionCode).apply();

            // 4. Test executing node --version with RPATH and LD_LIBRARY_PATH
            emitLog("[TEST] Testing Node execution: " + nodeFile.getAbsolutePath() + " -v");
            try {
                ProcessBuilder testPb = new ProcessBuilder(nodeFile.getAbsolutePath(), "-v");
                testPb.environment().put("LD_LIBRARY_PATH", libDir.getAbsolutePath());
                testPb.environment().put("OPENSSL_CONF", new File(filesDir, "openssl.cnf").getAbsolutePath());
                testPb.redirectErrorStream(true);
                Process testProc = testPb.start();

                BufferedReader r = new BufferedReader(new InputStreamReader(testProc.getInputStream()));
                String version = r.readLine();
                testProc.waitFor();
                emitLog("[TEST SUCCESS] Node.js verified! Output: " + version);
            } catch (Exception err) {
                emitLog("[TEST ERROR] Node.js test failed: " + err.getMessage());
            }

            // 5. Launch On-Device DSH Server & AtomicRouter Server
            File externalDir = Environment.getExternalStorageDirectory();
            File workspaceDir = (externalDir != null && externalDir.exists()) ? externalDir : new File("/sdcard");

            sanitizeProfileConfigs(filesDir);

            emitLog("[SERVER] Launching dsh --profile web --port 3080 ...");
            emitLog("[SERVER] Primary Workspace Directory: " + workspaceDir.getAbsolutePath());
            
            String enrichedPath = binDir.getAbsolutePath() + 
                    ":/data/adb/ksu/bin" + 
                    ":/data/adb/ap/bin" + 
                    ":/data/adb/magisk" + 
                    ":/sbin" + 
                    ":/system/bin" + 
                    ":/system/xbin" + 
                    ":/data/data/com.termux/files/usr/bin";

            String nodePath = (dshAsar.exists() ? (dshAsar.getAbsolutePath() + "/node_modules:") : (new File(dshDir, "node_modules").getAbsolutePath() + ":")) + 
                    new File(filesDir, ".dsh/profiles/web/node_modules").getAbsolutePath() +
                    ":" + new File(filesDir, "node_modules").getAbsolutePath();

            File asarPreload = new File(filesDir, "asar-engine/asar-preload.mjs");
            File asarRegister = new File(filesDir, "asar-engine/asar-register.cjs");
            boolean useAsar = dshAsar.exists() && asarPreload.exists();

            List<String> dshCmd = new ArrayList<>();
            dshCmd.add(nodeFile.getAbsolutePath());
            dshCmd.add("--max-old-space-size=512");
            if (useAsar) {
                emitLog("[SERVER] Launching in ASAR Zero-Extract Mode via " + dshAsar.getAbsolutePath());
                if (asarRegister.exists()) {
                    dshCmd.add("-r");
                    dshCmd.add(asarRegister.getAbsolutePath());
                }
                dshCmd.add("--import");
                dshCmd.add(asarPreload.getAbsolutePath());
                dshCmd.add(dshAsar.getAbsolutePath() + "/lib/bin.js");
            } else {
                emitLog("[SERVER] Launching in Directory Mode via " + dshBin.getAbsolutePath());
                dshCmd.add(dshBin.getAbsolutePath());
            }
            dshCmd.add("--profile");
            dshCmd.add("web");
            dshCmd.add("--no-open");
            dshCmd.add("--port");
            dshCmd.add("3080");

            ProcessBuilder pb = new ProcessBuilder(dshCmd);
            pb.directory(workspaceDir.exists() ? workspaceDir : filesDir);
            pb.environment().put("HOME", filesDir.getAbsolutePath());
            pb.environment().put("DSH_EXTERNAL_STORAGE", workspaceDir.getAbsolutePath());
            pb.environment().put("LD_LIBRARY_PATH", libDir.getAbsolutePath());
            pb.environment().put("NODE_PATH", nodePath);
            pb.environment().put("PATH", enrichedPath);
            pb.environment().put("DSH_PERMISSION_MODE", "danger-full-access");
            pb.environment().put("TMPDIR", filesDir.getAbsolutePath());
            pb.environment().put("OPENSSL_CONF", new File(filesDir, "openssl.cnf").getAbsolutePath());
            pb.redirectErrorStream(true);

            nodeProcess = pb.start();
            emitLog("[SERVER] DSH process spawned. Streaming logs:");

            // Spawn AtomicRouter if installed (ASAR or Directory)
            File atomicServerJs = new File(atomicDir, "server.js");
            File atomicBin = new File(atomicDir, "bin/omniroute.mjs");
            boolean useAtomicAsar = atomicAsar.exists() && asarPreload.exists();
            if (useAtomicAsar || atomicServerJs.exists() || atomicBin.exists()) {
                emitLog("[ATOMIC] Launching AtomicRouter on port 20128...");
                try {
                    List<String> atomicCmd = new ArrayList<>();
                    atomicCmd.add(nodeFile.getAbsolutePath());
                    atomicCmd.add("--max-old-space-size=512");
                    if (useAtomicAsar) {
                        if (asarRegister.exists()) {
                            atomicCmd.add("-r");
                            atomicCmd.add(asarRegister.getAbsolutePath());
                        }
                        atomicCmd.add("--import");
                        atomicCmd.add(asarPreload.getAbsolutePath());
                        atomicCmd.add(atomicAsar.getAbsolutePath() + "/server.js");
                    } else if (atomicServerJs.exists()) {
                        atomicCmd.add(atomicServerJs.getAbsolutePath());
                    } else {
                        atomicCmd.add(atomicBin.getAbsolutePath());
                        atomicCmd.add("serve");
                        atomicCmd.add("--port");
                        atomicCmd.add("20128");
                        atomicCmd.add("--no-open");
                    }

                    ProcessBuilder atomicPb = new ProcessBuilder(atomicCmd);
                    atomicPb.directory(workspaceDir.exists() ? workspaceDir : filesDir);
                    atomicPb.environment().put("HOME", filesDir.getAbsolutePath());
                    atomicPb.environment().put("DATA_DIR", new File(filesDir, ".atomic-router").getAbsolutePath());
                    atomicPb.environment().put("REQUIRE_API_KEY", "false");
                    atomicPb.environment().put("OMNIROUTE_API_KEY", "dsh-local-key");
                    atomicPb.environment().put("ROUTER_API_KEY", "dsh-local-key");
                    atomicPb.environment().put("PORT", "20128");
                    atomicPb.environment().put("HOSTNAME", "127.0.0.1");
                    atomicPb.environment().put("LD_LIBRARY_PATH", libDir.getAbsolutePath());
                    String atomicNodePath = (useAtomicAsar ? (atomicAsar.getAbsolutePath() + "/node_modules:") : (new File(atomicDir, "node_modules").getAbsolutePath() + ":")) + nodePath;
                    atomicPb.environment().put("NODE_PATH", atomicNodePath);
                    atomicPb.environment().put("PATH", enrichedPath);
                    atomicPb.environment().put("OPENSSL_CONF", new File(filesDir, "openssl.cnf").getAbsolutePath());
                    // Lightweight background mode: disable unnecessary background sync intervals when not actively in dashboard
                    atomicPb.environment().put("PROVIDER_LIMITS_SYNC_INTERVAL", "0");
                    atomicPb.environment().put("ARENA_ELO_SYNC", "false");
                    atomicPb.environment().put("PRICING_SYNC_ENABLED", "false");
                    atomicPb.redirectErrorStream(true);

                    atomicProcess = atomicPb.start();
                    new Thread(() -> {
                        try (BufferedReader r = new BufferedReader(new InputStreamReader(atomicProcess.getInputStream()))) {
                            String l;
                            while ((l = r.readLine()) != null) {
                                emitLog("[ATOMIC LOG] " + l);
                            }
                        } catch (Exception ignored) {}
                    }).start();
                } catch (Exception e) {
                    emitLog("[ATOMIC ERROR] Failed to start AtomicRouter: " + e.getMessage());
                }
            }

            // Stream Node Process output
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(nodeProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("__DSH_AGENT_REPLY__:")) {
                            try {
                                String jsonStr = line.substring("__DSH_AGENT_REPLY__:".length()).trim();
                                org.json.JSONObject obj = new org.json.JSONObject(jsonStr);
                                String reply = obj.optString("reply", "");
                                if (!reply.isEmpty()) {
                                    showAgentReplyNotification(reply);
                                }
                            } catch (Exception err) {
                                Log.e(TAG, "Failed to parse agent reply from stdout", err);
                            }
                            continue;
                        }
                        emitLog("[DSH Output] " + line);
                    }
                } catch (Exception e) {
                    emitLog("[SERVER STREAM ERROR] " + e.getMessage());
                }
            }).start();

            // Monitor nodeProcess exit
            new Thread(() -> {
                try {
                    int exitCode = nodeProcess.waitFor();
                    emitLog("[SERVER EXIT] DSH nodeProcess exited with code: " + exitCode);
                } catch (Exception ignored) {}
            }).start();


            // 6. Monitor port readiness for 3080
            int consecutiveSuccess = 0;
            for (int i = 1; i <= 60; i++) {
                try {
                    URL url = new URL("http://127.0.0.1:3080/");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(1000);
                    conn.setReadTimeout(1000);
                    int code = conn.getResponseCode();
                    if (code == 200) {
                        consecutiveSuccess++;
                        if (consecutiveSuccess >= 2) {
                            emitLog("[READY] DeepSeek Harness Engine ready on port 3080!");
                            emitReady();
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
                .setContentTitle("DSH & AtomicRouter Engine Active")
                .setContentText("Local AI Gateway running on port 3080 & 20128")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, notification);
        }
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "DSH Local Background Engine",
                        NotificationManager.IMPORTANCE_LOW
                );
                manager.createNotificationChannel(channel);

                NotificationChannel replyChannel = new NotificationChannel(
                        REPLY_CHANNEL_ID,
                        "DeepSeek Agent Replies",
                        NotificationManager.IMPORTANCE_HIGH
                );
                replyChannel.setDescription("Notifikasi balasan AI DeepSeek Harness");
                replyChannel.enableVibration(true);
                replyChannel.setVibrationPattern(new long[]{0, 200, 100, 200});
                replyChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                manager.createNotificationChannel(replyChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {}
        }
        try {
            unregisterReceiver(replyReceiver);
        } catch (Exception ignored) {}
        if (nodeProcess != null) {
            nodeProcess.destroy();
        }
        if (atomicProcess != null) {
            atomicProcess.destroy();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void sanitizeProfileConfigs(File filesDir) {
        try {
            // 1. Enforce strict chmod 600 on .credentials.yaml to prevent credentials-local 777 crash
            File creds = new File(filesDir, ".dsh/.credentials.yaml");
            if (creds.exists()) {
                creds.setReadable(true, true);
                creds.setWritable(true, true);
                creds.setExecutable(false, false);
                try {
                    Runtime.getRuntime().exec("chmod 600 " + creds.getAbsolutePath()).waitFor();
                } catch (Exception ignored) {}
            }

            // 2. Sanitize web profile package.json if broken bundles exist
            File webPkg = new File(filesDir, ".dsh/profiles/web/package.json");
            if (webPkg.exists()) {
                String content = new String(java.nio.file.Files.readAllBytes(webPkg.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                if (content.contains("multivers") && !content.contains("@aydin0411/dsh-multivers")) {
                    emitLog("[REPAIR] Detected outdated multivers bundle reference in web profile. Sanitizing...");
                    org.json.JSONObject pkgJson = new org.json.JSONObject(content);
                    org.json.JSONObject dshObj = pkgJson.optJSONObject("dsh");
                    if (dshObj != null) {
                        org.json.JSONObject profObj = dshObj.optJSONObject("profile");
                        if (profObj != null) {
                            org.json.JSONArray bundles = profObj.optJSONArray("bundles");
                            if (bundles != null) {
                                org.json.JSONArray newBundles = new org.json.JSONArray();
                                for (int i = 0; i < bundles.length(); i++) {
                                    String b = bundles.getString(i);
                                    if (!b.contains("multivers")) {
                                        newBundles.put(b);
                                    }
                                }
                                profObj.put("bundles", newBundles);
                                java.nio.file.Files.write(webPkg.toPath(), pkgJson.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                emitLog("[REPAIR SUCCESS] Web profile package.json sanitized successfully!");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            emitLog("[REPAIR WARN] Could not sanitize profile: " + e.getMessage());
        }
    }

    private boolean copyAssetFile(String assetPath, File outFile) {
        try {
            if (outFile.exists() && outFile.length() > 0) return true;
            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (InputStream in = getAssets().open(assetPath);
                 OutputStream out = new FileOutputStream(outFile)) {
                byte[] buf = new byte[65536];
                int len;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
            }
            return true;
        } catch (Exception e) {
            emitLog("[COPY ASSET ERROR] " + assetPath + ": " + e.getMessage());
            return false;
        }
    }

    private void copyAssetDir(String assetDir, File outDir) {
        try {
            String[] list = getAssets().list(assetDir);
            if (list == null || list.length == 0) return;
            if (!outDir.exists()) outDir.mkdirs();
            for (String file : list) {
                String subAsset = assetDir + "/" + file;
                File subOut = new File(outDir, file);
                String[] subList = getAssets().list(subAsset);
                if (subList != null && subList.length > 0) {
                    copyAssetDir(subAsset, subOut);
                } else {
                    copyAssetFile(subAsset, subOut);
                }
            }
        } catch (Exception e) {
            emitLog("[COPY DIR ERROR] " + assetDir + ": " + e.getMessage());
        }
    }
}
