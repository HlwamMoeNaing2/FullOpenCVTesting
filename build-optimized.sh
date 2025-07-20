#!/bin/bash

# Build script for optimized OpenCV APKs
echo "Building optimized APKs for OpenCV project..."

# Clean previous builds
./gradlew clean

# Build release APKs with ABI splits
echo "Building release APKs..."
./gradlew assembleRelease

# Check APK sizes
echo ""
echo "APK sizes:"
echo "=========="

# Find all APK files in the build directory
find app/build/outputs/apk/release -name "*.apk" -type f | while read apk; do
    size=$(du -h "$apk" | cut -f1)
    echo "$(basename "$apk"): $size"
done

echo ""
echo "Build completed! APK files are located in:"
echo "app/build/outputs/apk/release/"
echo ""
echo "To install on device:"
echo "adb install app/build/outputs/apk/release/app-arm64-v8a-release.apk"
echo "or"
echo "adb install app/build/outputs/apk/release/app-armeabi-v7a-release.apk" 