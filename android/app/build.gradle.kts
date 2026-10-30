import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.redmoon.vault"
    compileSdk = 35
    ndkVersion = "27.2.12479018"
    defaultConfig {
        applicationId = "com.redmoon.vault"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = false } }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}

val buildRust by
    tasks.registering(Exec::class) {
        workingDir(rootProject.projectDir.parentFile)
        val local =
            Properties().apply {
                val f = rootProject.file("local.properties")
                if (f.exists()) f.inputStream().use { load(it) }
            }
        val sdk =
            local.getProperty("sdk.dir")
                ?: System.getenv("ANDROID_HOME")
                ?: System.getenv("ANDROID_SDK_ROOT")
        if (sdk != null) environment("ANDROID_NDK_HOME", "$sdk/ndk/27.2.12479018")
        commandLine(
            "cargo",
            "ndk",
            "-t",
            "arm64-v8a",
            "-t",
            "x86_64",
            "-o",
            "android/app/src/main/jniLibs",
            "build",
            "-p",
            "red-moon-android",
            "--release",
        )
    }

tasks.named("preBuild").configure { dependsOn(buildRust) }

dependencies {
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
