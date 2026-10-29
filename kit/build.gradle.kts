plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

android {
    namespace = "desktopkit"
    compileSdk = 37

    defaultConfig {
        // Android 14: the platform's predictive Back, caption bar insets and haptic constants are
        // used directly, without fallbacks
        minSdk = 34
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Resources of the kit are prefixed so they never clash with the app's own.
    resourcePrefix = "dk_"

    testOptions {
        // the tests run as an app targeting the same Android version as the sample
        targetSdk = 36
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "2g"
                // Robolectric 4.17 reads FileDescriptor internals on Java 17+
                it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
                val screenshots = layout.buildDirectory.dir("outputs/screenshots")
                it.systemProperty("desktopkit.screenshotDir", screenshots.get().asFile.path)
                // declared so a test result restored from the build cache brings the screenshots along
                it.outputs.dir(screenshots).withPropertyName("screenshots")
                it.testLogging {
                    events("failed")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }
            }
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    api(libs.navigation.compose)
    // frosted glass for the chrome (bars, sheets, menus); see desktopkit.theme.DeskGlass.
    // HazeState is part of the kit's API (LocalGlassSource), the blur itself is internal.
    api(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.compose.ui.tooling.preview)
    // BackHandler for CompactTopBar; navigation-compose already brings it in
    implementation(libs.activity.compose)
    // WindowCompat (system bar icons), ViewCompat
    implementation(libs.core)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)
    testImplementation(libs.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.activity.compose)
    debugImplementation(libs.compose.ui.test.manifest)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.hesaplarimdijital-coder"
            artifactId = "desktop-kit"
            version = providers.gradleProperty("VERSION_NAME").get()
            afterEvaluate { from(components["release"]) }
        }
    }
}
