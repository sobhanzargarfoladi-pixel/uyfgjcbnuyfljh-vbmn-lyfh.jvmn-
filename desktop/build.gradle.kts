plugins {
    kotlin("jvm") version "2.0.21"
    application
}

repositories { mavenCentral() }

kotlin { jvmToolchain(17) }

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("io.netty:netty-handler:4.1.115.Final")
    implementation("io.netty:netty-codec-http2:4.1.115.Final")
    implementation("io.netty:netty-transport:4.1.115.Final")
    implementation("io.netty:netty-handler-proxy:4.1.115.Final")
}

// Reuse the platform-independent core of the Android app unchanged.
val syncShared by tasks.registering(Sync::class) {
    from("../app/src/main/java") {
        include(
            "com/foxyvpn/app/data/**",
            "com/foxyvpn/app/vpn/socks/**",
            "com/foxyvpn/app/vpn/upstream/**",
        )
        // Android-only storage classes; replaced by desktop versions in src/main/kotlin.
        exclude(
            "**/data/TokenStore.kt",
            "**/data/SettingsStore.kt",
            "**/data/CrashReporter.kt",
            "**/data/ProxyStateStore.kt",
        )
    }
    into(layout.buildDirectory.dir("shared-src"))
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(syncShared)
tasks.named("compileKotlin") { dependsOn(syncShared) }

application {
    applicationName = "FoxyVPN"
    mainClass.set("com.foxyvpn.desktop.MainKt")
}

tasks.jar { archiveFileName.set("foxyvpn-desktop.jar") }
