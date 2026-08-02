plugins { id("com.android.application") }
android { namespace="app.aapswear.mobile"; compileSdk=36; defaultConfig { applicationId="app.aapswear"; minSdk=26; targetSdk=36; versionCode=3; versionName="0.3.0" } }
dependencies { implementation(project(":data-source-aaps")); implementation(project(":wear-protocol")); implementation(project(":wear-storage")); implementation("com.google.android.gms:play-services-wearable:20.0.1"); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0"); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0") }
