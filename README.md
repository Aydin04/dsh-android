# 📱 DSH Mobile (Unofficial DeepSeek Harness Android Port)

> **⚠️ Disclaimer:**
> **DSH Mobile** adalah port/klien mandiri komunitas (**Unofficial Community Port**) untuk [DeepSeek Harness (DSH)](https://github.com/deepseek-ai/dsh). Proyek ini dikembangkan secara independen untuk menghadirkan pengalaman AI Agent DeepSeek Harness secara mandiri langsung di sistem operasi Android (ARM64).

---

## ✨ Fitur Utama

- 🐧 **Full Embedded PRoot + Alpine Linux Environment (`apk add`)**:
  - DSH Mobile membawa container mini-rootfs Alpine Linux ARM64 resmi.
  - Agent dapat menginstal dependensi pemrograman sendiri secara mandiri tanpa memerlukan Termux (`apk add python3 py3-pip`, `git`, `openssh`, `curl`, `gcc`, dll).
- 👑 **Supercharged Dual-Mode Superuser (Root `su`)**:
  - **Mode Standar (Non-Root)**: Berjalan aman di user-space via PRoot dengan akses penuh ke `/sdcard`.
  - **Mode Root (`👑 Root SU`)**: Memiliki akses `uid=0` langsung ke seluruh partisi sistem Android (`/system`, `/data`, `/data/adb`, kontrol hardware, dan kernel tweaks).
- 📂 **Direct `/sdcard` Workspace**:
  - Semua file proyek, skrip, dan kode yang dibuat agent langsung tersimpan di penyimpanan internal utama ponsel.
- 📝 **In-App Config Viewer & Editor (`📝 Config`)**:
  - Dilengkapi tombol cepat di toolbar untuk melihat dan mengedit file konfigurasi YAML server secara langsung dengan fitur *Save & Auto-Restart*.
- 🔍 **Native Ripgrep (`rg`) ARM64 v15.2.0**:
  - Pencarian berkas (`tools.glob`) dan teks kode (`tools.grep`) berkecepatan tinggi tanpa overhead.
- 🛡️ **Full Android Permissions**:
  - All Files Access (`MANAGE_EXTERNAL_STORAGE`), Network, Shizuku, WakeLock, dan Foreground Service.
- 🔄 **Automated CI/CD Upstream Sync**:
  - GitHub Actions otomatis memeriksa pembaruan resmi dari DeepSeek Harness setiap 6 jam dan membangun APK baru secara berkala.

---

## 📦 Unduh & Pasang APK

Anda dapat mengunduh file bundle APK rilis terbaru dari halaman **[Releases](https://github.com/Aydin04/dsh-android/releases)**.

Tersedia versi ZIP hemat kuota: **`dsh-mobile-compressed.zip`** (~110 MB). Cukup ekstrak file zip tersebut dan pasang `app-debug.apk` di HP Anda.

---

## 📄 Lisensi

Proyek port ini didistribusikan di bawah lisensi **MIT License**. DeepSeek Harness adalah hak cipta DeepSeek AI.
