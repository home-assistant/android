#!/system/bin/sh
# Enables HWAddressSanitizer (HWASan) for debug builds on ARM64 devices running Android 14+.
# HWASan detects memory errors (buffer overflows, use-after-free, double free, stack-use-after-return).
# See https://developer.android.com/ndk/guides/hwasan
LD_HWASAN=1 exec "$@"