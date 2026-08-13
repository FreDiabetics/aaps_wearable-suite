plugins {
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false

    id("com.android.application") version "9.3.1" apply false
    id("com.android.library") version "9.3.1" apply false
    kotlin("android") version "2.4.10" apply false
    kotlin("jvm") version "2.4.10" apply false
    kotlin("plugin.serialization") version "2.4.10" apply false
}

val watchFaceValidatorCli by configurations.creating

dependencies {
    watchFaceValidatorCli(
        "com.google.android.wearable.watchface.validator:validator-push-cli:1.0.0-alpha09",
    )
}

tasks.register<Copy>("prepareWatchFaceValidatorCli") {
    from(watchFaceValidatorCli)
    include("validator-push-cli-*.jar")
    into(layout.buildDirectory.dir("watchface-push/tools"))
}