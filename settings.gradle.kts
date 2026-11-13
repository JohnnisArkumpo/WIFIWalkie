pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Lets Gradle download the JDK set by jvmToolchain() if it isn't installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "wifi-walkie"

include("core", "desktop")

// The Android app needs the Android SDK. Without it (for example a desktop-only
// computer), skip the app so core and desktop still build.
val localProperties = java.util.Properties().apply {
    val file = File(settingsDir, "local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasAndroidSdk = localProperties.getProperty("sdk.dir") != null ||
    System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null
if (hasAndroidSdk) {
    include("android")
} else {
    println("Android SDK not found, so the Android app is skipped. Set sdk.dir in local.properties or ANDROID_HOME to build it.")
}
