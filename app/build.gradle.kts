import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// La clé de signature n'existe que sur GitHub Actions (secrets) : jamais dans le dépôt.
val keystorePath: String? = System.getenv("SENTE_KEYSTORE_PATH")

android {
    namespace = "io.github.piyaim.sente"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.piyaim.sente"
        minSdk = 26
        targetSdk = 36
        // Fournis par le workflow de préversion (-PversionCode=… -PversionName=…).
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "0.0.0-dev"

        // Code natif (Valhalla) pour les processeurs 64 bits seulement : appli plus légère.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SENTE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SENTE_KEY_ALIAS")
                keyPassword = System.getenv("SENTE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (keystorePath != null) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Pas de bloc de dépendances chiffré pour Google dans l'APK (exigé par F-Droid).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.maplibre.android)
    implementation(libs.valhalla.mobile)
}
