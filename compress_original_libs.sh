#!/bin/bash

# Script to compress original OpenCV .so files and place them in assets
# Original files location: /Users/hlwanmoenaing/Desktop/Testing App/FullOpenCVTesting/original_libs
# Target location: /Users/hlwanmoenaing/Desktop/Testing App/FullOpenCVTesting/app/src/main/assets/original_native_libs

echo "Starting OpenCV library compression from original files..."

# Navigate to project root
cd "/Users/hlwanmoenaing/Desktop/Testing App/FullOpenCVTesting"

# Create target directory structure
mkdir -p app/src/main/assets/original_native_libs/arm64-v8a
mkdir -p app/src/main/assets/original_native_libs/armeabi-v7a
mkdir -p app/src/main/assets/original_native_libs/x86
mkdir -p app/src/main/assets/original_native_libs/x86_64

# Copy original files to target directory
echo "Copying original files..."
cp original_libs/arm64-v8a/libopencv_java4.so app/src/main/assets/original_native_libs/arm64-v8a/
cp original_libs/armeabi-v7a/libopencv_java4.so app/src/main/assets/original_native_libs/armeabi-v7a/
cp original_libs/x86/libopencv_java4.so app/src/main/assets/original_native_libs/x86/
cp original_libs/x86_64/libopencv_java4.so app/src/main/assets/original_native_libs/x86_64/

# Compress each architecture's library
echo "Compressing arm64-v8a library..."
cd app/src/main/assets/original_native_libs/arm64-v8a
if [ -f "libopencv_java4.so" ]; then
    zip -q libopencv_java4.so.zip libopencv_java4.so
    echo "arm64-v8a: Original $(ls -lh libopencv_java4.so | awk '{print $5}') → Compressed $(ls -lh libopencv_java4.so.zip | awk '{print $5}')"
fi

echo "Compressing armeabi-v7a library..."
cd ../armeabi-v7a
if [ -f "libopencv_java4.so" ]; then
    zip -q libopencv_java4.so.zip libopencv_java4.so
    echo "armeabi-v7a: Original $(ls -lh libopencv_java4.so | awk '{print $5}') → Compressed $(ls -lh libopencv_java4.so.zip | awk '{print $5}')"
fi

echo "Compressing x86 library..."
cd ../x86
if [ -f "libopencv_java4.so" ]; then
    zip -q libopencv_java4.so.zip libopencv_java4.so
    echo "x86: Original $(ls -lh libopencv_java4.so | awk '{print $5}') → Compressed $(ls -lh libopencv_java4.so.zip | awk '{print $5}')"
fi

echo "Compressing x86_64 library..."
cd ../x86_64
if [ -f "libopencv_java4.so" ]; then
    zip -q libopencv_java4.so.zip libopencv_java4.so
    echo "x86_64: Original $(ls -lh libopencv_java4.so | awk '{print $5}') → Compressed $(ls -lh libopencv_java4.so.zip | awk '{print $5}')"
fi

# Remove original .so files from target directory (keep only compressed versions)
cd ..
find . -name "libopencv_java4.so" -delete

echo ""
echo "Compression completed successfully!"
echo "Compressed libraries are now available in:"
echo "app/src/main/assets/original_native_libs/"
echo ""
echo "File sizes:"
ls -lh */libopencv_java4.so.zip 