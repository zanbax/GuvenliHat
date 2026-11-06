plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.guvenlihat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.guvenlihat"
        minSdk = 24
        // 34: Android 15'in zorunlu kenardan kenara ekran düzenine girmemek için
        targetSdk = 34
        versionCode = 2
        versionName = "0.1.1"
    }

    // Sabit imza anahtarı: her yeni APK eskisinin üzerine güncelleme olarak kurulabilsin.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
