#!/system/bin/sh
# Enables HWAddressSanitizer for instrumented tests on ARM64 devices running Android 14+.
# See https://developer.android.com/ndk/guides/hwasan
LD_HWASAN=1 exec "$@"