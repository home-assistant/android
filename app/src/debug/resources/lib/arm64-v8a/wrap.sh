#!/system/bin/sh
# Enables debugging on device for different Android SDK versions
# See https://developer.android.com/ndk/guides/wrap-script#debugging_when_using_wrapsh
cmd=$1
shift

os_version=$(getprop ro.build.version.sdk)

if [ "$os_version" -eq "27" ]; then
  cmd="$cmd -Xrunjdwp:transport=dt_android_adb,suspend=n,server=y -Xcompiler-option --debuggable $@"
elif [ "$os_version" -eq "28" ]; then
  cmd="$cmd -XjdwpProvider:adbconnection -XjdwpOptions:suspend=n,server=y -Xcompiler-option --debuggable $@"
else
  cmd="$cmd -XjdwpProvider:adbconnection -XjdwpOptions:suspend=n,server=y $@"
fi

# Enables HWAddressSanitizer (HWASan) for debug builds on ARM64 devices running Android 14+.
# HWASan detects memory errors (buffer overflows, use-after-free, double free, stack-use-after-return).
# See https://developer.android.com/ndk/guides/hwasan
LD_HWASAN=1 exec $cmd