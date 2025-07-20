plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "mm.com.wavemoney.fullopencvtesting"
    compileSdk = 36

    defaultConfig {
        applicationId = "mm.com.wavemoney.fullopencvtesting"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Additional packaging optimizations
            packagingOptions {
                // Exclude unnecessary files
                exclude("META-INF/DEPENDENCIES")
                exclude("META-INF/LICENSE")
                exclude("META-INF/LICENSE.txt")
                exclude("META-INF/license.txt")
                exclude("META-INF/NOTICE")
                exclude("META-INF/NOTICE.txt")
                exclude("META-INF/notice.txt")
                exclude("META-INF/ASL2.0")
                exclude("META-INF/*.kotlin_module")
                
                // Exclude original OpenCV .so files since we're loading them from assets
                exclude("**/libopencv_java4.so")
            }
        }
    }
    
    // ABI splits to reduce APK size
//    splits {
//        abi {
//            isEnable = true
//            isUniversalApk = false
//            reset()
//            include("arm64-v8a", "armeabi-v7a")
//            // Exclude x86 and x86_64 for mobile-only app
//            // include("x86", "x86_64") // Uncomment if you need x86 support
//        }
//    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
    
//    // Native build configuration
//    externalNativeBuild {
//        cmake {
//            path = file("src/main/cpp/CMakeLists.txt")
//            version = "3.22.1"
//        }
//    }
//
//    // NDK configuration
//    ndkVersion = "25.2.9519653"
}

dependencies {
    val camerax_version = "1.3.1"
    // implementation(project(":openCvsdk")) // Commented out to reduce APK size
    implementation(project(":openCV440"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-video:${camerax_version}")
    implementation("androidx.camera:camera-extensions:${camerax_version}")
    implementation("pub.devrel:easypermissions:3.0.0")
}