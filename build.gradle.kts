plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt) apply false
}

// Applied at the root as well so the build scripts themselves are formatted; `./gradlew ktlintCheck` then covers
// both the root scripts and :app.
ktlint {
    version.set(libs.versions.ktlint.get())
}
