plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mckimquyen.cmonet"
    compileSdk = Apps.targetSdk

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    // Đồng bộ Java và Kotlin cùng dùng JVM 17
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    api(libs.core.ktx)
    api(libs.appcompat)
    api(libs.material)
//    testImplementation(libs.test.junit)
//    androidTestImplementation(libs.test.ext.junit)
//    androidTestImplementation(libs.test.espresso.core)
}
