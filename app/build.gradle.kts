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
        versionCode = 9
        versionName = "0.6.2"
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

    // Kalıcı imza: anahtar depoda şifreli durur (release.jks.enc); GitHub derlemesi
    // SIGNING_PASSWORD gizli değeriyle çözüp release.jks olarak kullanır.
    val signingPassword: String? = System.getenv("SIGNING_PASSWORD")
    val releaseKey = file("release.jks")
    val hasReleaseKey = !signingPassword.isNullOrEmpty() && releaseKey.exists()
    if (hasReleaseKey) {
        signingConfigs.create("release") {
            storeFile = releaseKey
            storePassword = signingPassword
            keyAlias = "guvenlihat"
            keyPassword = signingPassword
            storeType = "PKCS12"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
