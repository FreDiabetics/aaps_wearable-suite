plugins { id("com.android.application") }
android {
    namespace="app.aapswear.wear"
    compileSdk=37
    defaultConfig { applicationId="app.aapswear"; minSdk=30; targetSdk=36; versionCode=12; versionName="0.6.2" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
dependencies {
    implementation(project(":wear-protocol"))
    implementation(project(":wear-storage"))
    implementation(project(":complications"))
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
