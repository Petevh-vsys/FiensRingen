plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "be.ringenbeheer"
    compileSdk = 35

    defaultConfig {
        applicationId = "be.ringenbeheer"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
    }
}

kotlin {
    jvmToolchain(17)
}
