plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mybalance.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mybalance.app"
        minSdk = 26
        targetSdk = 36
        // Su GitHub il numero di versione cresce a ogni build, cosi' Android accetta sempre l'aggiornamento.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0"
    }

    signingConfigs {
        // Chiave fissa (file app/mybalance.jks): tutte le build hanno la stessa firma
        // e si installano sopra la versione precedente senza disinstallare.
        create("mybalance") {
            storeFile = file("mybalance.jks")
            storePassword = "mybalance2026"
            keyAlias = "mybalance"
            keyPassword = "mybalance2026"
        }
    }

    // Chiave di caricamento per Google Play (solo quando la build e' avviata con i "Secrets" di GitHub).
    val uploadFile = System.getenv("UPLOAD_KEYSTORE_FILE")
    if (uploadFile != null) {
        signingConfigs {
            create("upload") {
                storeFile = file(uploadFile)
                storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (uploadFile != null) {
                signingConfigs.getByName("upload")
            } else {
                signingConfigs.getByName("mybalance")
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
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
}
