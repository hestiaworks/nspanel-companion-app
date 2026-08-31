# WebRTC's native library registers its JNI entry points by looking Java
# classes up by name, through the jni_zero bridge. R8 cannot see those
# lookups, so without these rules it renames the classes, the native side
# fails to find org.jni_zero.JniInit at load, and traps — a SIGTRAP inside
# libjingle_peerconnection_so.so with no Java stack, in release builds only.
-keep class org.webrtc.** { *; }
-keep class org.jni_zero.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
