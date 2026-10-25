plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.example.dayhub"
    compileSdk = 35
    defaultConfig { applicationId = "com.example.dayhub"; minSdk = 26; targetSdk = 35; versionCode = 4; versionName = "0.3" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

 dependencies { implementation("androidx.work:work-runtime-ktx:2.10.2") }
