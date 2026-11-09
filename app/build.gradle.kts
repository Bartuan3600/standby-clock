plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "de.bartuan.standbyclock"
    compileSdk = 35
    defaultConfig {
        applicationId = "de.bartuan.standbyclock"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.4"
    }
    signingConfigs {
        create("release") {
            storeFile = file("../keystore/standbyclock.jks")
            storePassword = "standbyclock"
            keyAlias = "standbyclock"
            keyPassword = "standbyclock"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}
