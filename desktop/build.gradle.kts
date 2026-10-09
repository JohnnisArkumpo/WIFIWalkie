plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(25)
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
