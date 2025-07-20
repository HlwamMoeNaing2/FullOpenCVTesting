package mm.com.wavemoney.fullopencvtesting.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

object NativeLibraryLoader {
    private const val TAG = "NativeLibraryLoader"
    private const val LIB_NAME = "libopencv_java4.so"
    private const val COMPRESSED_LIB_NAME = "libopencv_java4.so.zip"
    
    @Volatile
    private var isLibraryLoaded = false
    
    fun loadOpenCVLibrary(context: Context): Boolean {
        if (isLibraryLoaded) {
            return true
        }
        
        synchronized(this) {
            if (isLibraryLoaded) {
                return true
            }
            
            try {
                val libraryFile = extractLibraryFromAssets(context)
                if (libraryFile != null && libraryFile.exists()) {
                    System.load(libraryFile.absolutePath)
                    isLibraryLoaded = true
                    Log.d(TAG, "OpenCV library loaded successfully from: ${libraryFile.absolutePath}")
                    return true
                } else {
                    Log.e(TAG, "Failed to extract OpenCV library from assets")
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading OpenCV library", e)
                return false
            }
        }
    }
    
    private fun extractLibraryFromAssets(context: Context): File? {
        val libDir = File(context.filesDir, "native_libs")
        if (!libDir.exists()) {
            libDir.mkdirs()
        }
        
        val libraryFile = File(libDir, LIB_NAME)
        
        // Check if library already exists and is not corrupted
        if (libraryFile.exists() && libraryFile.length() > 0) {
            return libraryFile
        }
        
        // Get the appropriate architecture folder
        val archFolder = getArchitectureFolder()
        val assetPath = "native_libs/$archFolder/$COMPRESSED_LIB_NAME"
        
        try {
            context.assets.open(assetPath).use { inputStream ->
                ZipInputStream(inputStream).use { zipInputStream ->
                    var entry = zipInputStream.nextEntry
                    while (entry != null) {
                        if (entry.name == LIB_NAME) {
                            FileOutputStream(libraryFile).use { outputStream ->
                                zipInputStream.copyTo(outputStream)
                            }
                            libraryFile.setExecutable(true)
                            Log.d(TAG, "Extracted OpenCV library to: ${libraryFile.absolutePath}")
                            return libraryFile
                        }
                        entry = zipInputStream.nextEntry
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error extracting library from assets", e)
        }
        
        return null
    }
    
    private fun getArchitectureFolder(): String {
        return when (System.getProperty("os.arch")) {
            "aarch64" -> "arm64-v8a"
            "armv7l" -> "armeabi-v7a"
            "i686" -> "x86"
            "x86_64" -> "x86_64"
            else -> "arm64-v8a" // Default to arm64-v8a
        }
    }
} 