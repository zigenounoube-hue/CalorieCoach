plugins {
    id("com.android.application") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("com.google.gms.google-services")
}

android {
    namespace = "fr.caloriecoach.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "fr.caloriecoach.app"
        testInstrumentationRunner = "fr.caloriecoach.app.AiDiagnostics"
        buildConfigField("boolean", "APP_CHECK_DEBUG", providers.gradleProperty("appCheckDebug").orElse("false").get())
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.1.3"
        buildConfigField("String", "GEMINI_API_KEY", "\"\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.9.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-ai")
    implementation("com.google.firebase:firebase-appcheck-debug")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
}

