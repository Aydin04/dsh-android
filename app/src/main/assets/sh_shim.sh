#!/system/bin/sh
# Universal Shell Shim for DSH Mobile
# Priority 1: If proot with Alpine rootfs exists, run command inside proot
ROOTFS="/data/user/0/com.dsh.mobile/files/rootfs"
PROOT="/data/user/0/com.dsh.mobile/files/bin/proot"

if [ -f "$PROOT" ] && [ -d "$ROOTFS/bin" ]; then
    export PROOT_TMP_DIR="/data/user/0/com.dsh.mobile/files"
    exec "$PROOT" -0 -r "$ROOTFS" -b /sdcard:/sdcard -b /data/user/0/com.dsh.mobile/files:/dsh_app -w /sdcard /bin/sh "$@"
fi

# Priority 2: Fallback to system /system/bin/sh
exec /system/bin/sh "$@"
