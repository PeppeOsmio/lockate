plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "com.peppeosmio.jpake"
        compileSdk = 37
        minSdk = 31
    }

    // Only used to run the tests on the build machine; iOS targets get added with the iOS client
    jvm()

    sourceSets {
        // The JNA binding to libjpake is shared by android and jvm, which need a different JNA
        // artifact each, so it's compiled into both instead of being an intermediate source set
        androidMain.configure { kotlin.srcDir("src/jnaMain/kotlin") }
        jvmMain.configure { kotlin.srcDir("src/jnaMain/kotlin") }

        commonMain.dependencies {
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
        }
        androidMain.dependencies {
            implementation(project(":jpake-native"))
            implementation("${libs.jna.get()}@aar")
        }
        jvmMain.dependencies {
            implementation(libs.jna)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// libjpake for the machine running the tests, from the same sources as the Android build
val jpakeSources = rootProject.layout.projectDirectory.dir("jpake-native/src/main/cpp")
val jpakeHostDir = layout.buildDirectory.dir("jpake-host")

val buildJpakeHost = tasks.register<Exec>("buildJpakeHost") {
    inputs.dir(jpakeSources)
    inputs.dir(rootProject.layout.projectDirectory.dir("third_party/mbedtls/library"))
    inputs.dir(rootProject.layout.projectDirectory.dir("third_party/mbedtls/include"))
    outputs.dir(jpakeHostDir)
    val build = jpakeHostDir.get().asFile.absolutePath
    commandLine(
        "sh", "-c",
        "cmake -S '${jpakeSources.asFile.absolutePath}' -B '$build' -DCMAKE_BUILD_TYPE=Release " +
            "&& cmake --build '$build' --parallel"
    )
}

tasks.named<Test>("jvmTest") {
    dependsOn(buildJpakeHost)
    systemProperty("jna.library.path", jpakeHostDir.get().asFile.absolutePath)
}
