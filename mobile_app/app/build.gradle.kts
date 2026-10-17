import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.peppeosmio.lockate"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.peppeosmio.lockate"
        minSdk = 31
        targetSdk = 36
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = findProperty("versionName") as String? ?: "1.0"

    }

    signingConfigs {
        create("release") {
            val local = Properties()
            rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { local.load(it) }
            fun cfg(prop: String, env: String) = local.getProperty(prop) ?: System.getenv(env)
            cfg("lockate.keystore.path", "LOCKATE_KEYSTORE_PATH")?.let { storeFile = file(it) }
            storePassword = cfg("lockate.keystore.password", "LOCKATE_KEYSTORE_PASSWORD")
            keyAlias = cfg("lockate.key.alias", "LOCKATE_KEY_ALIAS")
            keyPassword = cfg("lockate.key.password", "LOCKATE_KEY_PASSWORD")
        }
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    // bouncycastle
    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true // if true, also builds a fat APK with all ABIs
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.mock)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // https://ktor.io/docs/client-dependencies.html#engine-dependency
    implementation(libs.io.ktor.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.websockets)

    implementation(platform(libs.koin.bom))
    implementation(libs.insert.koin.koin.core)
    implementation(libs.insert.koin.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(libs.bcprov.jdk18on)

    implementation(libs.cryptography.bigint)
    implementation(libs.cryptography.core)
    implementation(libs.cryptography.provider.optimal)


    implementation(libs.play.services.location)

    implementation(libs.maplibre.compose)
    implementation(libs.maplibre.composeMaterial3)
    // maplibre-compose ships no renderer: the app picks one. OpenGL is what Compose draws with on Android
    implementation(libs.maplibre.nativeFfi.runtime.opengl)

}
