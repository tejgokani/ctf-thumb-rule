plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tejgokani.strata"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tejgokani.strata"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Both build types share ONE signing identity and ONE applicationId (plan §13 risk #2: an
    // Android OAuth client is keyed on package name + signing SHA-1, so a debug/release split
    // that changes either would silently require a second Google Cloud OAuth client and split
    // Drive access between builds). No `applicationIdSuffix` here — that is deliberate.
    signingConfigs {
        create("strata") {
            val ksFile = rootProject.file("strata.keystore")
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = System.getenv("STRATA_KEYSTORE_PASSWORD") ?: "strata-dev-only"
                keyAlias = "strata"
                keyPassword = System.getenv("STRATA_KEY_PASSWORD") ?: "strata-dev-only"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            if (rootProject.file("strata.keystore").exists()) {
                signingConfig = signingConfigs.getByName("strata")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (rootProject.file("strata.keystore").exists()) {
                signingConfig = signingConfigs.getByName("strata")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)
    implementation(libs.play.services.auth)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.datastore.preferences)
    implementation(libs.biometric)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.runtime)
}
