plugins {
    alias(libs.plugins.android.library)
}

// Only packages libjpake.so (Mbed TLS EC-JPAKE, see src/main/cpp); the Kotlin side is in :jpake
android {
    namespace = "com.peppeosmio.lockate.jpake_native"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 31
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
