plugins { id("com.android.application") }
android {
    enableKotlin = false
    namespace = "app.aapswear.watchface.sugarlicious.rings"
    compileSdk = 36
    defaultConfig { applicationId = "app.aapswear.watchface.sugarlicious.rings"; minSdk = 33; targetSdk = 35; versionCode = 1; versionName = "0.6.0" }
    buildTypes { release { isMinifyEnabled = true; isShrinkResources = false; signingConfig = signingConfigs.getByName("debug") } }
    buildFeatures { buildConfig = false }
    sourceSets.getByName("main").res.setSrcDirs(
        listOf("src/main/res", "../sugarlicious-shared/res"),
    )
    packaging { resources.excludes += setOf("kotlin/**", "META-INF/*.version", "META-INF/*.kotlin_module") }
    lint { checkReleaseBuilds = false }
}
configurations.configureEach { if (name.endsWith("RuntimeClasspath") || name.endsWith("CompileClasspath")) exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib") }
