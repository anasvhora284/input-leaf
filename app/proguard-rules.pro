# Keep Compose and Material3 classes
-dontwarn kotlin.**
-dontwarn kotlinx.**

# Keep Shizuku API
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# Keep AIDL interfaces
-keep class com.inputleaf.android.service.IInputInjector** { *; }

# Keep data classes
-keep class com.inputleaf.android.model.** { *; }

# Keep Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep R8 from removing things needed at runtime
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations

# Keep application classes
-keep class com.inputleaf.android.** { *; }
