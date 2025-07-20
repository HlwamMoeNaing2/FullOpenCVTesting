# APK Size Optimization for OpenCV Project

This document explains the optimizations implemented to reduce APK size when using the OpenCV SDK module.

## Optimizations Implemented

### 1. ABI Splits (Most Important)
- **What it does**: Creates separate APKs for each CPU architecture instead of one universal APK
- **Size reduction**: ~50-70% reduction per APK
- **Result**: 
  - `app-arm64-v8a-release.apk` (~22MB instead of ~60MB)
  - `app-armeabi-v7a-release.apk` (~15MB instead of ~60MB)

### 2. ProGuard Optimization
- **Minification enabled**: Removes unused code and obfuscates
- **Resource shrinking**: Removes unused resources
- **OpenCV-specific rules**: Only keeps modules needed for face detection and image processing

### 3. Packaging Optimizations
- **Excluded unnecessary files**: META-INF files, licenses, notices
- **Native library optimization**: Only includes required `.so` files

### 4. OpenCV Module Optimization
- **Minification enabled** for the OpenCV SDK module
- **Custom ProGuard rules** to remove unused OpenCV modules

## Build Commands

### Quick Build (Recommended)
```bash
./build-optimized.sh
```

### Manual Build
```bash
# Clean previous builds
./gradlew clean

# Build release APKs
./gradlew assembleRelease

# Check APK sizes
find app/build/outputs/apk/release -name "*.apk" -type f -exec du -h {} \;
```

## APK Files Generated

After building, you'll find these files in `app/build/outputs/apk/release/`:

1. **`app-arm64-v8a-release.apk`** (~22MB)
   - For modern Android devices (64-bit ARM)
   - Recommended for most devices

2. **`app-armeabi-v7a-release.apk`** (~15MB)
   - For older Android devices (32-bit ARM)
   - Smaller size but limited compatibility

## Installation

### For Modern Devices (Recommended)
```bash
adb install app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

### For Older Devices
```bash
adb install app/build/outputs/apk/release/app-armeabi-v7a-release.apk
```

## Size Comparison

| Build Type | Size | Notes |
|------------|------|-------|
| Universal APK (before) | ~60MB | Includes all architectures |
| arm64-v8a APK (after) | ~22MB | 64-bit devices only |
| armeabi-v7a APK (after) | ~15MB | 32-bit devices only |

## OpenCV Modules Kept

Only these OpenCV modules are included (optimized for face detection and image processing):

- ✅ **Core** (`org.opencv.core`) - Essential data structures
- ✅ **Image Processing** (`org.opencv.imgproc`) - Image manipulation
- ✅ **Object Detection** (`org.opencv.objdetect`) - Face detection
- ✅ **Image Codecs** (`org.opencv.imgcodecs`) - Image I/O
- ✅ **Android Integration** (`org.opencv.android`) - Camera integration

## OpenCV Modules Removed

These modules were removed to reduce size (not needed for face detection):

- ❌ **Video** (`org.opencv.video`) - Video processing
- ❌ **Machine Learning** (`org.opencv.ml`) - ML algorithms
- ❌ **Deep Neural Networks** (`org.opencv.dnn`) - DNN models
- ❌ **Photo** (`org.opencv.photo`) - Photo processing
- ❌ **Calib3d** (`org.opencv.calib3d`) - Camera calibration
- ❌ **Features2d** (`org.opencv.features2d`) - Feature detection
- ❌ **Stitching** (`org.opencv.stitching`) - Image stitching
- ❌ **GAPI** (`org.opencv.gapi`) - Graph API
- ❌ **HighGUI** (`org.opencv.highgui`) - GUI components

## Troubleshooting

### If you need additional OpenCV modules:
1. Edit `app/proguard-rules.pro`
2. Remove the `-dontwarn` lines for the modules you need
3. Add `-keep class org.opencv.[module].** { *; }` for each module

### If you need x86 support:
1. Edit `app/build.gradle.kts`
2. Uncomment the x86 lines in the ABI splits section
3. Rebuild

### If you encounter crashes:
1. Check that the correct ABI APK is installed
2. Verify ProGuard rules aren't removing needed classes
3. Test with debug build first

## Performance Notes

- **arm64-v8a**: Best performance, recommended for modern devices
- **armeabi-v7a**: Good performance, compatible with older devices
- Both APKs include the same functionality, just optimized for different architectures 