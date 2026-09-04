// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.dynamic.feature) apply false
    alias(libs.plugins.kotlin.android) apply false
}

// Every Zenithblue dynamic-feature asset merge / pre-build must run AFTER the app
// staging task, so a clean bundleZenithblueRelease automatically stages all 12
// rootfs archives before any feature package merges them. Ivarna tasks are
// untouched and never require Play staging.
gradle.projectsEvaluated {
    val stageTask = rootProject.tasks.findByPath(":app:stagePlayRootfsFeatures")
        ?: return@projectsEvaluated
    for (sub in rootProject.childProjects.values) {
        if (!sub.name.startsWith("distro_")) continue
        sub.tasks
            .matching { t: org.gradle.api.Task ->
                val n = t.name
                (n.startsWith("merge") && "Zenithblue" in n && n.endsWith("Assets")) ||
                    (n.startsWith("pre") && "Zenithblue" in n && n.endsWith("Build"))
            }
            .configureEach { dependsOn(stageTask) }
    }
}
