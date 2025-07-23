The .so files in the original_native_libs is the .so files for Default OpenCv 4.11.0. So when you want to use original default 4.11.0, 
1. Add original open cv android sdk
2. delete .so files under native/lib/
3. Prepare gradle configuration
4. change asset paths for   val assetPath = "native_libs/$archFolder/$COMPRESSED_LIB_NAME" and val libDir = File(context.filesDir, "native_libs") -> native_libs to original_native_libs