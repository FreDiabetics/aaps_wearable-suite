plugins {
    id("org.jetbrains.kotlin.plugin.compose")
 id("com.android.application") }
android {
    buildFeatures { compose = true }

    namespace="app.aapswear.mobile"
    compileSdk=36
    defaultConfig { applicationId="app.aapswear"; minSdk=26; targetSdk=36; versionCode=7; versionName="0.5.1" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation(project(":data-source-aaps"))
    implementation(project(":wear-protocol"))
    implementation(project(":wear-storage"))
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
