# Preserve Xposed framework hooks and entry points
-keep class com.example.vcam.HookMain { *; }
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# Preserve all VCAM core classes & config fields (used across processes & JSON serialization)
-keep class com.example.vcam.VCamConfig { *; }
-keep class com.example.vcam.AudioInjector { *; }
-keep class com.example.vcam.AntiDetection { *; }
-keep class com.example.vcam.VideoToFrames { *; }
-keep class com.example.vcam.FloatingControllerService { *; }

# Preserve line numbers for readable crash stacktraces in Xposed logs
-keepattributes SourceFile,LineNumberTable