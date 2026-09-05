# Keep AndroidX & Compose classes
-keep class androidx.** { *; }
-dontwarn androidx.**

# OpenCV rules if needed
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# MochiStitch specific rules
-keep class com.mochistitch.** { *; }
