plugins {
    id("com.android.dynamic-feature")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ivarna.fluxlinux.distro_opensuse"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "store"
    productFlavors {
        create("ivarna") {
            dimension = "store"
        }
        create("zenithblue") {
            dimension = "store"
        }
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        noCompress += listOf("xz", "tar", "gz")
    }
}

dependencies {
    implementation(project(":app"))
}
