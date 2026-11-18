plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.yomu"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.yomu"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Clé fixe : les mises à jour s'installent par-dessus sans désinstaller.
    signingConfigs {
        create("yomu") {
            storeFile = file("yomu.keystore")
            storePassword = "yomu-store"
            keyAlias = "yomu"
            keyPassword = "yomu-store"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("yomu")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("yomu")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
