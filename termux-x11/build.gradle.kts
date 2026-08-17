plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.termux.x11"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "COMMIT", "\"1.03.01\"")
        buildConfigField("String", "VERSION_NAME", "\"1.03.01\"")
        // libXlorie.so is compiled from source (upstream lorie native tree);
        // Ivarna packages arm64-v8a only (x86_64 runs it via NDK translation).
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // Compile libXlorie.so from source: vendored lorie native tree + 16
    // dependency submodules under src/main/cpp/ (pinned upstream commits).
    // Toolchain at build time: Python3 + Bison + patch (see docs/plans).
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        aidl = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")

    // Compile-only stub module
    compileOnly(project(":stub"))
}
