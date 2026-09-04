plugins {
    id("com.android.dynamic-feature")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ivarna.fluxlinux.distro_alpine"
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

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    doLast {
        val assetsDir = file("build/intermediates/assets/${name.removePrefix("merge").removeSuffix("Assets").replaceFirstChar { it.lowercase() }}/$name/payloads/distro_alpine")
        val uncompressedTar = file("$assetsDir/alpine_3.24_rootfs.tar")
        val targetGz = file("$assetsDir/alpine_3.24_rootfs.tar.gz")
        val stagedSource = rootProject.file("distro_alpine/src/zenithblue/assets/payloads/distro_alpine/alpine_3.24_rootfs.tar.gz")
        if (uncompressedTar.exists() && !targetGz.exists() && stagedSource.exists()) {
            println("[distro_alpine] Restoring untouched alpine_3.24_rootfs.tar.gz in $assetsDir")
            stagedSource.copyTo(targetGz, overwrite = true)
            uncompressedTar.delete()
        }
    }
}

dependencies {
    implementation(project(":app"))
}
