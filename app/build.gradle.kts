plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.guvenlihat"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.guvenlihat"
        minSdk = 24
        // Google Play: yeni uygulamalar ve güncellemeler Android 16'yı (API 36) hedeflemeli
        targetSdk = 36
        versionCode = 16
        versionName = "0.9.1"
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

    buildFeatures {
        buildConfig = true
    }

    // İki sürüm, tek kod:
    //  full: bütün özellikler (APK, testçilere doğrudan dağıtım)
    //  play: çekirdek özellikler (AAB, Google Play). Konum, hareketsizlik, kullanım erişimi
    //        ve tam zamanlı alarm izinleri bu sürümde yok (src/play/AndroidManifest.xml).
    flavorDimensions += "dagitim"
    productFlavors {
        create("full") {
            dimension = "dagitim"
            buildConfigField("boolean", "CARE_FEATURES", "true")
        }
        create("play") {
            dimension = "dagitim"
            buildConfigField("boolean", "CARE_FEATURES", "false")
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
