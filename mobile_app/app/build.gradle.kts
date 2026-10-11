import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization") version "2.2.10"
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.peppeosmio.lockate"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.peppeosmio.lockate"
        minSdk = 31
        targetSdk = 36
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = findProperty("versionName") as String? ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.ktor.client.mock)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.datetime)

    // https://ktor.io/docs/client-dependencies.html#engine-dependency
    implementation(libs.io.ktor.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.websockets)

    implementation(project.dependencies.platform("io.insert-koin:koin-bom:4.1.0"))
    implementation(libs.insert.koin.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.compose.viewmodel.navigation)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.sqlite.bundled)

    implementation(libs.bcprov.jdk18on)

    implementation(libs.cryptography.bigint)
    implementation(libs.cryptography.core)
    implementation(libs.cryptography.provider.optimal)

    implementation(libs.androidx.runtime.livedata)

    implementation(libs.play.services.location)

    implementation(libs.maplibre.compose)
    implementation(libs.maplibre.composeMaterial3)

    implementation(libs.composeSettings.ui)
    implementation(libs.composeSettings.ui.extended)
}
