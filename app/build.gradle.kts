import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing is optional: without a keystore the release build is signed
// with the debug key so that anyone can build the project from source.
// Point NEUROPAD_KEYSTORE_PROPERTIES (or keystore.properties in the repo root,
// which is git-ignored) at a file with storeFile/storePassword/keyAlias/keyPassword.
val keystorePropsFile = (System.getenv("NEUROPAD_KEYSTORE_PROPERTIES")?.let { file(it) }
    ?: rootProject.file("keystore.properties"))
val keystoreProps = Properties().apply {
    if (keystorePropsFile.isFile) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "io.github.abdullahamr871.neuropad"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.abdullahamr871.neuropad"
        minSdk = 24
        targetSdk = 34
        versionCode = 20000
        versionName = "2.0.0"
        vectorDrawables.useSupportLibrary = true
        // The bundled uinput_proto daemon is a 64-bit ARM binary, so other ABIs cannot work.
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Not minified on purpose: the ADB and TLS libraries are driven by reflection, and
            // a shrinker mistake there would only show up on a real phone. The rules in
            // proguard-rules.pro are kept for anyone who wants to turn this on.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf(
            "META-INF/{AL2.0,LGPL2.1}",
            "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            "META-INF/*.kotlin_module",
        )
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
            all {
                // -Pscreenshots renders every screen to docs/screenshots (see ScreenshotTest).
                val screenshots = project.hasProperty("screenshots")
                it.systemProperty("neuropad.screenshots", screenshots.toString())
                it.systemProperty("roborazzi.test.record", screenshots.toString())
                it.maxHeapSize = "2g"
            }
        }
    }
    lint { abortOnError = false }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Rootless shell access: ADB client that talks to the phone's own adbd.
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("org.conscrypt:conscrypt-android:2.5.2")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.46.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.46.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
