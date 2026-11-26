import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.walkrun.wear"
    compileSdk = 35

    defaultConfig {
        // Same application id as the phone app: the Wear OS Data Layer connects only apps
        // with the same id and the same signing key.
        applicationId = "com.example.walkrun"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Same key as the phone app: the Data Layer links only apps signed with the same key.
    val signingProps = rootProject.file("signing/keystore.properties")
    val walkrunKey = if (signingProps.exists()) {
        val p = Properties().apply { signingProps.inputStream().use { load(it) } }
        signingConfigs.create("walkrun") {
            storeFile = rootProject.file("signing/" + p.getProperty("storeFile"))
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias")
            keyPassword = p.getProperty("keyPassword")
        }
    } else null

    buildTypes {
        debug {
            walkrunKey?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            signingConfig = walkrunKey ?: signingConfigs.getByName("debug")
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
}

dependencies {
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.wear.compose:compose-material:1.4.1")
    implementation("androidx.wear.compose:compose-foundation:1.4.1")
    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
}
