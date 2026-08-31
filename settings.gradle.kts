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
include(":distro_fedora")
include(":distro_void")
include(":distro_opensuse")
include(":distro_chimera")
include(":distro_deepin")
include(":distro_manjaro")
include(":distro_ubuntu")
include(":distro_kali")
include(":distro_parrot")
include(":distro_arch")
