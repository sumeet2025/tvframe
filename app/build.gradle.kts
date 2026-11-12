import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing: point to a keystore.properties file that is NOT committed.
// Lookup order: env TVFRAME_KEYSTORE_PROPERTIES, then `tvframe.keystoreProperties` in
// local.properties, then ./keystore.properties at the repo root. If none exists, the
// release build is produced unsigned (debug builds are unaffected).
// keystore.properties keys: storeFile, storePassword, keyAlias, keyPassword
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val keystorePropsFile: File? = listOfNotNull(
    System.getenv("TVFRAME_KEYSTORE_PROPERTIES"),
    localProps.getProperty("tvframe.keystoreProperties"),
    rootProject.file("keystore.properties").path
).map { file(it) }.firstOrNull { it.exists() }
val keystoreProps = Properties().apply {
    keystorePropsFile?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.nostr.tvframe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nostr.tvframe"
        // Minimum SDK 30 (Android TV 11); works on any Android TV / Google TV device running Android 11+
        minSdk = 30
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (keystorePropsFile != null) {
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
            if (keystorePropsFile != null) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn("TVFrame: no keystore.properties found, release APK will be unsigned.")
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Jetpack Compose for TV
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    // Lifecycle & Activity Compose
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore & Encrypted SharedPreferences (for SMB credentials)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.documentfile)

    // WorkManager for Background Pre-Fetching & Cache Pruning
    implementation(libs.androidx.work.runtime.ktx)

    // Networking & Serialization (Wikimedia Commons API)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Image Loading & Disk Caching
    implementation(libs.coil.compose)

    // SMBv2 / SMBv3 Network Share Client
    implementation(libs.jcifs.ng)

    // ZXing QR Code Generator
    implementation(libs.zxing.core)

    // Unit tests (pure Kotlin logic: category mixing, sync paging)
    testImplementation(libs.junit)
}
