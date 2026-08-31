plugins {
    id("com.android.dynamic-feature")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ivarna.fluxlinux.distro_arch"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig { minSdk = 26 }
    flavorDimensions += "store"
    productFlavors {
        create("ivarna") { dimension = "store" }
        create("zenithblue") { dimension = "store" }
    }
    androidResources { @Suppress("UnstableApiUsage") noCompress += listOf("gz", "xz", "tar") }
}
dependencies { implementation(project(":app")) }
