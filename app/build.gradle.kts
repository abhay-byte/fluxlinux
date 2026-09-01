import org.gradle.api.tasks.bundling.AbstractArchiveTask
import java.util.Properties

val playReleaseFeatureModules = listOf(
    "runtime_host",
    "distro_debian",
    "distro_alpine",
    "distro_ubuntu",
    "distro_kali",
    "distro_arch",
    "distro_manjaro",
    "distro_chimera",
)

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
        versionCode = 12
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Dual app-id flavors: ivarna (F-Droid/GitHub) + zenithblue (Play).
    // Directly executed host launchers remain in base jniLibs. The large Play
    // bootstrap/rootfs archives are staged into on-demand dynamic features.
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
        // Critical — do not recompress archives; they must stay STORED in the APK.
        // Executable-bearing archives are not base-module assets. Feature
        // modules use the same no-compress policy for byte-exact staging.
        @Suppress("UnstableApiUsage")
        noCompress += listOf("gz", "xz", "tar")
    }

    dynamicFeatures += playReleaseFeatureModules.map { ":$it" }

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
            // F-Droid has no keystore.properties; an empty signingConfig
            // ("release" without storeFile) fails assemble. Sign only locally.
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
// Package directly executed host assets per flavor (Pass 2 fail-closed gate)
//   :app:packageHostAssetsIvarna     — stages native/bootstrap/com.ivarna.fluxlinux
//   :app:packageHostAssetsZenithblue — stages native/bootstrap/com.zenithblue.fluxlinux
// Fails assemble with a clear error when native jniLibs are missing. Play
// bootstrap/rootfs inputs are staged into on-demand dynamic features by
// preparePlayPayloads, never into the base module.
// ─────────────────────────────────────────────────────────────────────────────
val flavorAppIds = mapOf(
    "ivarna" to "com.ivarna.fluxlinux",
    "zenithblue" to "com.zenithblue.fluxlinux"
)

for ((flavorName, appId) in flavorAppIds) {
    val taskName = "packageHostAssets" + flavorName.replaceFirstChar { it.uppercase() }
    tasks.register<Exec>(taskName) {
        group = "build"
        description = "Stage directly executed host jniLibs for flavor '$flavorName' from native/bootstrap/$appId"
        workingDir = rootProject.projectDir
        commandLine("bash", "scripts/package_host_assets.sh", appId)

        val bootstrapTree = fileTree(rootProject.file("native/bootstrap/$appId"))
        inputs.files(bootstrapTree)
        outputs.dir(file("src/$flavorName/jniLibs"))

        doFirst {
            val jniRequired = listOf(
                rootProject.file("native/bootstrap/$appId/jniLibs/arm64-v8a/libbash.so"),
                rootProject.file("native/bootstrap/$appId/jniLibs/arm64-v8a/libproot.so"),
                rootProject.file("native/bootstrap/$appId/jniLibs/arm64-v8a/libloader.so"),
                rootProject.file("native/bootstrap/$appId/jniLibs/arm64-v8a/libloader32.so"),
                rootProject.file("native/bootstrap/$appId/jniLibs/arm64-v8a/libpulseaudio.so"),
                rootProject.file("native/bootstrap/$appId/jniLibs/arm64-v8a/libpactl.so")
            )
            val missing = jniRequired.filter { !it.isFile }
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

// The pinned termux-app AAR supplies the Java terminal classes and a legacy
// 4 KB libtermux.so. Build the tiny JNI source locally with an explicit 16 KB
// linker setting and put it in the app source set, which AGP gives precedence
// over the duplicate AAR native entry. The generated directory is ignored and
// is recreated on every clean checkout/build.
tasks.register<Exec>("prepareTermuxNative16k") {
    group = "build"
    description = "Build the pinned Termux terminal JNI library with 16 KB LOAD alignment"
    workingDir = rootProject.projectDir
    commandLine("bash", "scripts/build_termux_terminal_native_16k.sh")
    inputs.files(fileTree(rootProject.file("native/third_party/termux-terminal-emulator")))
    outputs.dir(file("src/main/jniLibs"))
}

val playPayloadSourceRoot = providers.gradleProperty("playPayloadSourceRoot")
    .orElse(rootProject.file("assets/rootfs").absolutePath)
val playAlpineSource = providers.gradleProperty("playAlpineSource")
val playHostBootstrapSource = providers.gradleProperty("playHostBootstrapSource")
    .orElse(rootProject.file("native/bootstrap/com.zenithblue.fluxlinux/bootstrap.tar").absolutePath)
val playPayloadOutputDirs = listOf(
    *playReleaseFeatureModules.toTypedArray()
).map { rootProject.file("$it/src/zenithblue/assets/payloads") }

tasks.register<Exec>("preparePlayPayloads") {
    group = "build"
    description = "Verify and stage Play payloads into ignored dynamic-feature assets"
    workingDir = rootProject.projectDir
    val prepareArgs = mutableListOf(
        "python3", "scripts/prepare_play_payloads.py",
        "--source-root", playPayloadSourceRoot.get(),
        "--host-source", playHostBootstrapSource.get()
    )
    playAlpineSource.orNull?.let {
        prepareArgs += listOf("--alpine-source", it)
        inputs.file(it)
    }
    commandLine(prepareArgs)
    inputs.files(fileTree(playPayloadSourceRoot))
    inputs.file(playHostBootstrapSource)
    outputs.dirs(playPayloadOutputDirs)
}

tasks.register<Exec>("verifyPlayPayloadSizes") {
    group = "verification"
    description = "Enforce Play feature and cumulative payload size safety margins"
    workingDir = rootProject.projectDir
    commandLine(
        "python3",
        "scripts/verify_play_payload_sizes.py",
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--base-estimate-bytes",
        (500L * 1024L * 1024L).toString(),
    )
    dependsOn("preparePlayPayloads")
    inputs.files(playPayloadOutputDirs)
}

// The generated payload directories are outputs of this app task but are
// consumed by the dynamic-feature projects. Wire the cross-project dependency
// explicitly so Gradle cannot run a feature asset merge before staging finishes.
gradle.projectsEvaluated {
    playReleaseFeatureModules.map { ":$it" }.forEach { featurePath ->
        rootProject.project(featurePath).tasks
            .matching { it.name.contains("Zenithblue") }
            .configureEach { dependsOn(":app:preparePlayPayloads") }
    }
}

// Every assemble / bundle / pre-build for a flavor depends on its native
// package task. Play builds additionally stage all dynamic-feature payloads.
for (flavorName in flavorAppIds.keys) {
    val cap = flavorName.replaceFirstChar { it.uppercase() }
    tasks.matching {
        it.name.startsWith("assemble$cap") ||
            it.name.startsWith("bundle$cap") ||
            it.name.startsWith("pre${cap}")
    }.configureEach {
        dependsOn("packageHostAssets$cap")
        dependsOn("prepareTermuxNative16k")
        if (flavorName == "zenithblue") dependsOn("verifyPlayPayloadSizes")
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

    // Google Play Feature Delivery is referenced only by the zenithblue source
    // set at runtime; the dependency is harmless in the non-Play variant and
    // keeps the provider boundary compile-time separated.
    implementation(libs.play.feature.delivery)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
