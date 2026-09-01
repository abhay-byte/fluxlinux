pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "FluxLinux"
include(":app")
include(":termux-x11")
include(":stub")
include(":runtime_host")
include(":distro_debian")
include(":distro_alpine")
include(":distro_chimera")
include(":distro_manjaro")
include(":distro_ubuntu")
include(":distro_kali")
include(":distro_arch")

// Fedora, Void, openSUSE, Deepin, and Parrot source directories remain in the
// repository for Ivarna/common support, but are not registered as Gradle
// dynamic-feature projects in the fast Play v2.0 release graph.
