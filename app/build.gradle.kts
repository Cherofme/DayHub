plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.example.dayhub"
    compileSdk = 35
    defaultConfig { applicationId = "com.example.dayhub"; minSdk = 26; targetSdk = 35; versionCode = 7; versionName = "0.3.3" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.10.2")
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
