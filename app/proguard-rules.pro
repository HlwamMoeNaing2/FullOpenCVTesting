# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# OpenCV optimization rules - Keep only modules needed for face detection and image processing
-keep class org.opencv.** { *; }
-keep class org.opencv.android.** { *; }
-keep class org.opencv.core.** { *; }
-keep class org.opencv.imgproc.** { *; }
-keep class org.opencv.objdetect.** { *; }
-keep class org.opencv.imgcodecs.** { *; }

# Optimize native library loading
-keep class org.opencv.android.OpenCVLoader { *; }
-keep class org.opencv.android.CameraBridgeViewBase { *; }

# Remove unused OpenCV modules to reduce APK size
# These modules are not needed for face detection and basic image processing
-dontwarn org.opencv.stitching.**
-dontwarn org.opencv.gapi.**
-dontwarn org.opencv.videoio.**
-dontwarn org.opencv.video.**
-dontwarn org.opencv.ml.**
-dontwarn org.opencv.dnn.**
-dontwarn org.opencv.photo.**
-dontwarn org.opencv.calib3d.**
-dontwarn org.opencv.features2d.**
-dontwarn org.opencv.flann.**
-dontwarn org.opencv.highgui.**

# Additional optimizations
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

# Keep only essential OpenCV classes
-keep class org.opencv.core.Mat { *; }
-keep class org.opencv.core.MatOfRect { *; }
-keep class org.opencv.core.Rect { *; }
-keep class org.opencv.core.Point { *; }
-keep class org.opencv.core.Scalar { *; }
-keep class org.opencv.core.Size { *; }
-keep class org.opencv.objdetect.CascadeClassifier { *; }
-keep class org.opencv.imgproc.Imgproc { *; }