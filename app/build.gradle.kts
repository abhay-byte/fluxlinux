import org.gradle.api.tasks.bundling.AbstractArchiveTask
import java.util.Properties

// F-Droid reproducible builds: disable baseline profiles using Groovy script
apply(from = "fix-baseline-profiles.gradle")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ivarna.fluxlinux"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.ivarna.fluxlinux"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "1.8.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Dual app-id flavors: ivarna (F-Droid/GitHub) + zenithblue (Play).
    // Host bootstrap + jniLibs ship per flavor from native/bootstrap/<applicationId>/
    // staged by :app:packageHostAssets* (runs scripts/package_host_assets.sh).
    flavorDimensions += "store"
    productFlavors {
        create("ivarna") {
            dimension = "store"
            applicationId = "com.ivarna.fluxlinux"
            // Host binaries (libbash/loader/proot) build only for arm64-v8a;
            // x86_64 devices run them via NDK translation (native bridge).
            ndk { abiFilters += listOf("arm64-v8a") }
        }
        create("zenithblue") {
            dimension = "store"
            applicationId = "com.zenithblue.fluxlinux"
        }
    }

    androidResources {
        // Disable PNG crunching for reproducible builds
        @Suppress("UnstableApiUsage")
        ignoreAssetsPattern = "!.svn:!.git:.*:!CVS:!thumbs.db:!picasa.ini:!*.scc:*~"
        // Critical — do not recompress archives; they must stay STORED in the APK
        @Suppress("UnstableApiUsage")
        // .minirootfs = Alpine gzip payload under non-.gz name (aapt2 strips *.gz).
        noCompress += listOf("xz", "tar", "minirootfs")
    }

    // Disable dependency metadata block for F-Droid
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(keystorePropertiesFile.inputStream())
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            packaging {
                resources.excludes.add("META-INF/**")
            }
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Disable baseline profiles for F-Droid reproducible builds
            packaging {
                resources.excludes.add("META-INF/**")
                resources.excludes.add("**.prof")
                resources.excludes.add("assets/dexopt/baseline.prof")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests {
            // android.util.Log etc. are stubs in JVM tests — return defaults instead of throwing
            isReturnDefaultValues = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // W^X host binaries (libbash/libproot/libloader) must be extracted to
            // nativeLibraryDir so the app-data ET_DYN loader can exec them.
            useLegacyPackaging = true
        }
    }
}

// Reproducible builds configuration for F-Droid
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// ─────────────────────────────────────────────────────────────────────────────
// Package host assets per flavor (Pass 2 fail-closed gate, plan P0-T6/T7)
//   :app:stageHostRootfs             — shared rootfs asset (once)
//   :app:packageHostAssetsIvarna     — stages native/bootstrap/com.ivarna.fluxlinux
//   :app:packageHostAssetsZenithblue — stages native/bootstrap/com.zenithblue.fluxlinux
// Fails assemble with a clear error when bootstrap.tar / jniLibs are missing.
// The shared rootfs lives in its own task so two flavor tasks never write the
// same output concurrently.
// ─────────────────────────────────────────────────────────────────────────────
val flavorAppIds = mapOf(
    "ivarna" to "com.ivarna.fluxlinux",
    "zenithblue" to "com.zenithblue.fluxlinux"
)

val stageHostRootfs = tasks.register<Exec>("stageHostRootfs") {
    group = "build"
    description =
        "Copy pinned Debian + Alpine + Fedora + Void + openSUSE + Deepin + " +
            "Chimera + Manjaro + Ubuntu + Kali + Parrot + Arch rootfs into " +
            "app/src/main/assets/rootfs " +
            "(Alpine as .minirootfs so aapt2 does not strip .gz)"
    workingDir = rootProject.projectDir
    // Alpine must not be packaged as *.tar.gz: aapt2 auto-decompresses and
    // renames to *.tar, breaking SHA + AssetManager.open("….tar.gz").
    commandLine(
        "bash", "-c",
        """
        set -euo pipefail
        mkdir -p app/src/main/assets/rootfs
        if [ ! -f app/src/main/assets/rootfs/debian_13_rootfs.tar.xz ] && \
           [ -f assets/rootfs/debian_13_rootfs.tar.xz ]; then
          cp -f assets/rootfs/debian_13_rootfs.tar.xz app/src/main/assets/rootfs/debian_13_rootfs.tar.xz
        fi
        if [ -f assets/rootfs/alpine_3.24_rootfs.tar.gz ]; then
          # Always refresh packaged name (gzip bytes, non-.gz extension).
          cp -f assets/rootfs/alpine_3.24_rootfs.tar.gz \
            app/src/main/assets/rootfs/alpine_3.24_rootfs.minirootfs
          # Remove legacy name so aapt never sees *.tar.gz
          rm -f app/src/main/assets/rootfs/alpine_3.24_rootfs.tar.gz
        fi
        for rf in fedora_44_rootfs.tar.xz void_20250202_rootfs.tar.xz \
                 opensuse_tumbleweed_rootfs.tar.xz \
                 deepin_25_rootfs.tar.xz chimera_20251220_rootfs.tar.xz \
                 manjaro_arm_rootfs.tar.xz \
                 ubuntu_26.04_rootfs.tar.xz kali_2026_2_rootfs.tar.xz \
                 parrot_7.2_rootfs.tar.xz archlinux_arm_rootfs.tar.xz; do
          if [ -f "assets/rootfs/${'$'}rf" ]; then
            cp -f "assets/rootfs/${'$'}rf" "app/src/main/assets/rootfs/${'$'}rf"
          fi
        done
        """.trimIndent()
    )
    inputs.files(
        rootProject.file("assets/rootfs/debian_13_rootfs.tar.xz"),
        rootProject.file("assets/rootfs/alpine_3.24_rootfs.tar.gz"),
        rootProject.file("assets/rootfs/fedora_44_rootfs.tar.xz"),
        rootProject.file("assets/rootfs/void_20250202_rootfs.tar.xz"),
        rootProject.file("assets/rootfs/opensuse_tumbleweed_rootfs.tar.xz"),
        rootProject.file("assets/rootfs/deepin_25_rootfs.tar.xz"),
        rootProject.file("assets/rootfs/chimera_20251220_rootfs.tar.xz"),
        rootProject.file("assets/rootfs/manjaro_arm_rootfs.tar.xz"),
        rootProject.file("assets/rootfs/ubuntu_26.04_rootfs.tar.xz"),
        rootProject.file("assets/rootfs/kali_2026_2_rootfs.tar.xz"),
        rootProject.file("assets/rootfs/parrot_7.2_rootfs.tar.xz"),
        rootProject.file("assets/rootfs/archlinux_arm_rootfs.tar.xz")
    )
    outputs.files(
        file("src/main/assets/rootfs/debian_13_rootfs.tar.xz"),
        file("src/main/assets/rootfs/alpine_3.24_rootfs.minirootfs"),
        file("src/main/assets/rootfs/fedora_44_rootfs.tar.xz"),
        file("src/main/assets/rootfs/void_20250202_rootfs.tar.xz"),
        file("src/main/assets/rootfs/opensuse_tumbleweed_rootfs.tar.xz"),
        file("src/main/assets/rootfs/deepin_25_rootfs.tar.xz"),
        file("src/main/assets/rootfs/chimera_20251220_rootfs.tar.xz"),
        file("src/main/assets/rootfs/manjaro_arm_rootfs.tar.xz"),
        file("src/main/assets/rootfs/ubuntu_26.04_rootfs.tar.xz"),
        file("src/main/assets/rootfs/kali_2026_2_rootfs.tar.xz"),
        file("src/main/assets/rootfs/parrot_7.2_rootfs.tar.xz"),
        file("src/main/assets/rootfs/archlinux_arm_rootfs.tar.xz")
    )
}

for ((flavorName, appId) in flavorAppIds) {
    val taskName = "packageHostAssets" + flavorName.replaceFirstChar { it.uppercase() }
    tasks.register<Exec>(taskName) {
        group = "build"
        description = "Stage host bootstrap + jniLibs for flavor '$flavorName' from native/bootstrap/$appId"
        workingDir = rootProject.projectDir
        commandLine("bash", "scripts/package_host_assets.sh", appId)
        dependsOn(stageHostRootfs)

        val bootstrapTree = fileTree(rootProject.file("native/bootstrap/$appId"))
        inputs.files(bootstrapTree)
        outputs.file(file("src/$flavorName/assets/bootstrap.tar"))
        outputs.dir(file("src/$flavorName/jniLibs"))

        doFirst {
            val missing = listOf(
                rootProject.file("native/bootstrap/$appId/bootstrap.tar"),
                rootProject.file("native/bootstrap/$appId/jniLibs/arm64-v8a/libbash.so"),
                rootProject.file("native/bootstrap/$appId/jniLibs/arm64-v8a/libproot.so"),
                rootProject.file("native/bootstrap/$appId/jniLibs/arm64-v8a/libloader.so"),
                rootProject.file("native/bootstrap/$appId/jniLibs/arm64-v8a/libloader32.so")
            ).filter { !it.isFile }
            if (missing.isNotEmpty()) {
                throw GradleException(
                    "Host bootstrap assets missing for applicationId '$appId':\n" +
                        missing.joinToString("\n") { "  - $it" } +
                        "\n\nRun the package build first:\n" +
                        "  ./scripts/build_packages_for_appid.sh $appId\n" +
                        "  ./scripts/assemble_bootstrap.py --package-name $appId --mode full\n" +
                        "  ./scripts/verify_bootstrap.sh $appId"
                )
            }
        }
    }
}

// Every assemble / bundle / pre-build for a flavor depends on its package task so
// mergeAssets / mergeJniLibFolders never see an empty host (P0-T7).
for (flavorName in flavorAppIds.keys) {
    val cap = flavorName.replaceFirstChar { it.uppercase() }
    tasks.matching {
        it.name.startsWith("assemble$cap") ||
            it.name.startsWith("bundle$cap") ||
            it.name.startsWith("pre${cap}")
    }.configureEach {
        dependsOn("packageHostAssets$cap")
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
    implementation(libs.androidx.material.icons.extended)
    
    // Glassmorphism FX
    implementation(libs.haze)
    implementation(libs.haze.materials)
    
    // Permissions
    implementation(libs.accompanist.permissions)
    
    // Networking
    implementation(libs.okhttp)

    // Embedded terminal (termux-app GPLv3 — app stays open source; see LICENSE/README)
    implementation(libs.termux.app)
    implementation(libs.listenablefuture)

    // Embedded Termux:X11 (cloned + integrated directly, same-package rendering)
    implementation(project(":termux-x11"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
