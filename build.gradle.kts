plugins {
    kotlin("jvm") version "2.4.21" apply false
    // Only applied when the Android SDK is installed (see settings.gradle.kts).
    id("com.android.application") version "9.0.1" apply false
    kotlin("plugin.compose") version "2.4.21" apply false
}
