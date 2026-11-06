plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.callguard"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.callguard"
        minSdk = 29
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.1"
    }

    // Release signing material never lives in the repo. CI exports the four variables below; when they are
    // absent (a laptop build) the release build is signed with the debug key so assembleRelease still works.
    val releaseKeystore = providers.environmentVariable("CALLGUARD_KEYSTORE")
    val signedForRelease = releaseKeystore.isPresent

    signingConfigs {
        if (signedForRelease) {
            create("release") {
                storeFile = file(releaseKeystore.get())
                storePassword = providers.environmentVariable("CALLGUARD_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("CALLGUARD_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("CALLGUARD_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (signedForRelease) "release" else "debug")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        // Compose screens need real resources and a manifest under Robolectric.
        unitTests.isIncludeAndroidResources = true
    }
}

ktlint {
    version.set(libs.versions.ktlint.get())
    android.set(false)
}

// detekt 2.x (coordinates dev.detekt, the successor of io.gitlab.arturbosch.detekt). The 1.23.x line bundles
// Kotlin 2.0's shaded IntelliJ JavaVersion, which throws IllegalArgumentException on java.runtime.version "25.x",
// so it cannot analyse on the JDK 25 that AGP 9 / Android Studio ship. Config is the stock generated default.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    baseline = rootProject.file("config/detekt/baseline.xml")
    source.setFrom(
        files(
            "src/main/java",
            "src/test/java",
            "src/androidTest/java",
        ),
    )
}

// detekt defaults its analysis target to the running JDK, which is newer than the bytecode this app compiles to.
val detektJvmTarget = "17"

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget = detektJvmTarget
    parallel = true
}

tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach {
    jvmTarget = detektJvmTarget
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
