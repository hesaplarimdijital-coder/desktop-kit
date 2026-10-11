plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "desktopkit.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "desktopkit.sample"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // In your own app: implementation("com.github.hesaplarimdijital-coder:desktop-kit:<version>")
    implementation(project(":kit"))
    implementation(libs.activity.compose)
}
