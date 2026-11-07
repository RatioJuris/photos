plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Optional: keep build outputs outside a synced folder (OneDrive/Dropbox lock files while syncing).
// Set `photos.buildRoot=/some/local/path` in ~/.gradle/gradle.properties to enable.
providers.gradleProperty("photos.buildRoot").orNull?.let { root ->
    allprojects {
        layout.buildDirectory.set(file("$root/${rootProject.name}/${project.name}"))
    }
}
