#!/system/bin/sh
# Universal Hybrid Shell Shim for DSH Mobile
# Support: Native Root SU (Magisk / KernelSU / APatch), PRoot Alpine Linux, and Android Bionic

ROOT_FLAG_FILE="/data/user/0/com.dsh.mobile/files/root_enabled.flag"
ROOT_FLAG_ALT="/data/data/com.dsh.mobile/files/root_enabled.flag"
ROOTFS="/data/user/0/com.dsh.mobile/files/rootfs"
[ ! -d "$ROOTFS" ] && ROOTFS="/data/data/com.dsh.mobile/files/rootfs"
PROOT="/data/user/0/com.dsh.mobile/files/bin/proot"
[ ! -f "$PROOT" ] && PROOT="/data/data/com.dsh.mobile/files/bin/proot"

# 1. If Root SU is enabled by user or root flag exists, execute directly with SU in host environment
if [ -f "$ROOT_FLAG_FILE" ] || [ -f "$ROOT_FLAG_ALT" ] || [ "$(id -u 2>/dev/null)" = "0" ]; then
    for su_bin in /system/bin/su /system/xbin/su /data/adb/ksu/bin/su /data/adb/ap/bin/su /data/adb/magisk/su su; do
        if command -v "$su_bin" >/dev/null 2>&1 || [ -x "$su_bin" ]; then
            if [ "$1" = "-c" ]; then
                shift
                exec "$su_bin" -mm -c "$*" 2>/dev/null || exec "$su_bin" -c "$*"
            else
                exec "$su_bin" -mm -c "$*" 2>/dev/null || exec "$su_bin" -c "$*"
            fi
        fi
    done
fi

# 2. Non-root / Standard: If proot with Alpine rootfs exists, run command inside proot
if [ -x "$PROOT" ] && [ -d "$ROOTFS/bin" ]; then
    APP_FILES="/data/user/0/com.dsh.mobile/files"
    [ ! -d "$APP_FILES" ] && APP_FILES="/data/data/com.dsh.mobile/files"
    export LD_LIBRARY_PATH="$APP_FILES/lib:$LD_LIBRARY_PATH"
    export PROOT_TMP_DIR="$APP_FILES/tmp"
    mkdir -p "$PROOT_TMP_DIR" 2>/dev/null || true

    exec "$PROOT" -0 -r "$ROOTFS" \
        -b /sdcard:/sdcard \
        -b /storage/emulated/0:/storage/emulated/0 \
        -b "$APP_FILES":/dsh_app \
        -b /system:/system \
        -b /system/bin:/system/bin \
        -b /dev:/dev \
        -b /proc:/proc \
        -b /sys:/sys \
        -w /sdcard /bin/sh "$@"
fi

# 3. Fallback: Host Android /system/bin/sh
exec /system/bin/sh "$@"

