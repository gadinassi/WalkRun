import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.walkrun"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.walkrun"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // OAuth 2.0 *Web application* client ID from Google Cloud Console.
        // An Android client with this package name and the signing SHA-1 must exist in the same project.
        buildConfigField("String", "WEB_CLIENT_ID", "\"246895549277-cupplv185nkdj0iaids9139b6f9rek5c.apps.googleusercontent.com\"")
    }

    // The permanent WalkRun key (signing/keystore.properties, not in git).
    // Its SHA-1 is registered in Google Cloud, so sign-in, Gmail, Drive and updates depend on it.
    // Without the file (for example on a new computer), the build falls back to the local debug key.
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = walkrunKey ?: signingConfigs.getByName("debug")
        }
    }
    testOptions {
        // JVM unit tests: Android calls (like Log) return default values instead of throwing.
        unitTests.isReturnDefaultValues = true
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
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    // Authorization for the Gmail send scope (automatic report emails).
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    // Health Connect: workouts, distance, steps and calories also appear in Google Fit and other apps.
    implementation("androidx.health.connect:connect-client:1.1.0-alpha12")
    // Wear OS watch app: live data to the watch, pause/resume/stop from the watch.
    implementation("com.google.android.gms:play-services-wearable:18.2.0")

    testImplementation("junit:junit:4.13.2")
}
