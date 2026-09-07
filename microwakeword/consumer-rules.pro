# The native library binds these methods by exact name and signature via RegisterNatives on the
# MicroWakeWord class (see src/main/cpp/MicroWakeWord_jni.cpp). R8 must not rename, move, or strip
# the class or its native methods, or JNI_OnLoad fails with UnsatisfiedLinkError.
-keepclasseswithmembers class io.homeassistant.companion.android.microwakeword.MicroWakeWord* {
    native <methods>;
}
