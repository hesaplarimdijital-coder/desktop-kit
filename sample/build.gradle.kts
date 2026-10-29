plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "desktopkit.sample"
    compileSdk = 37

    defaultConfig {
        applicationId = "desktopkit.sample"
        minSdk = 34
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // In your own app: implementation("com.github.hesaplarimdijital-coder:desktop-kit:<version>")
    implementation(project(":kit"))
    implementation(libs.activity.compose)
}
