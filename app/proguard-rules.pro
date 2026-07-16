# WatchoutRF ProGuard Rules

# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep NativeLib class
-keep class com.watchoutrf.app.NativeLib { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# USB Device Info (used via reflection in some Android USB APIs)
-keep class com.watchoutrf.app.data.usb.UsbDeviceInfo { *; }
