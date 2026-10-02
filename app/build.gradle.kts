plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.autovoicecaller"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.autovoicecaller"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0-mvp"
        buildConfigField("String", "TWILIO_SID", "\"${System.getenv("TWILIO_SID") ?: "YOUR_TWILIO_SID"}\"")
        buildConfigField("String", "TWILIO_TOKEN", "\"${System.getenv("TWILIO_TOKEN") ?: "YOUR_TWILIO_TOKEN"}\"")
        buildConfigField("String", "TWILIO_FROM_NUMBER", "\"${System.getenv("TWILIO_FROM_NUMBER") ?: "+12569045291"}\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    buildTypes { release { isMinifyEnabled = false } }
}
dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
}
