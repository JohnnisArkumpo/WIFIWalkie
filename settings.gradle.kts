plugins {
    // Lets Gradle download the JDK set by jvmToolchain() if it isn't installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "wifi-walkie"

include("core", "desktop")
