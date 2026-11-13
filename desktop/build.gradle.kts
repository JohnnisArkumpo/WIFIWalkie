import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(25)
    // Built with JDK 25 but runs on any Java 17 or newer, however it's started.
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.add("-Xjdk-release=17")   // compile error if desktop uses an API newer than Java 17
    }
}

java {
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":core"))
}

application {
    mainClass = "wifiwalkie.desktop.MainKt"
    applicationName = "wifi-walkie"
}

tasks.named<JavaExec>("run") {
    // Pass typed input through to the program's prompts.
    standardInput = System.`in`
}
