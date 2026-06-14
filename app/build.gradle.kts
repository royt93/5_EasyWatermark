import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.kapt")
    id("dagger.hilt.android.plugin")
}

android {
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    namespace = "com.mckimquyen.watermark"

    defaultConfig {
        applicationId = "com.mckimquyen.watermark"
        minSdk = 24
        targetSdk = 36
        versionCode = 20260615
        versionName = "2026.06.15"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        setProperty("archivesBaseName", "$applicationId-v$versionName($versionCode)")

        buildConfigField("String", "APPLOVIN_SDK_KEY", "\"e75FnQfS9XTTqM1Kne69U7PW_MBgAnGQTFvtwVVui6kRPKs5L7ws9twr5IQWwVfzPKZ5pF2IfDa7lguMgGlCyt\"")
        buildConfigField("String", "APPLOVIN_BANNER_ID", "\"d3455cc529985b25\"")
        buildConfigField("String", "APPLOVIN_INTERSTITIAL_ID", "\"a48241ebcb20ad5c\"")
        buildConfigField("String", "APPLOVIN_APP_OPEN_ID", "\"8239a7fd6896cf1f\"")
        buildConfigField("String", "APPLOVIN_REWARDED_ID", "\"87a4f5696dfd4212\"")
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"https://loitp.notion.site/Term-Privacy-Policy-Disclaimer-319b1cd8783942fa8923d2a3c9bce60f\"")
    }

    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            force("androidx.core:core-ktx:1.15.0")
            force("androidx.core:core:1.15.0")
            force("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
        }
    }

    signingConfigs {
        create("release") {
            keyAlias = findProperty("KEY_ALIAS") as String?
            keyPassword = findProperty("KEY_PASSWORD") as String?
            val storeFileName = findProperty("STORE_FILE") as String?
            if (storeFileName != null) {
                storeFile = file(storeFileName)
            }
            storePassword = findProperty("STORE_PASSWORD") as String?
        }
    }
    flavorDimensions.add("default")
    productFlavors {
        create("appTest") {
        }
        create("appRelease") {
        }
    }

    buildTypes {
        val debug by getting {
//            applicationIdSuffix = ".debug"
            buildConfigField("Boolean", "IS_ENABLE_ADMOB", "false")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "ADMOB_APP_OPEN_ID", "\"ca-app-pub-3940256099942544/9257395921\"")
            buildConfigField("String", "ADMOB_REWARDED_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
        }

        val release by getting {
            // nho check APPLICATION_ID trong manifest
            buildConfigField("Boolean", "IS_ENABLE_ADMOB", "false")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3612191981543807/3976595378\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3612191981543807/2663513707\"")
            buildConfigField("String", "ADMOB_APP_OPEN_ID", "\"ca-app-pub-3612191981543807/6718308789\"")
            buildConfigField("String", "ADMOB_REWARDED_ID", "\"ca-app-pub-3940256099942544/5224354917\"")

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "coroutines.pro",
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_11)
        targetCompatibility(JavaVersion.VERSION_11)
    }

    // change output apk name
    applicationVariants.all {
        outputs.all {
            (this as? BaseVariantOutputImpl)?.outputFileName =
                "$applicationId-v$versionName($versionCode).apk"
        }
    }

    packagingOptions {
        resources.excludes.add("DebugProbesKt.bin")
    }

    android.buildFeatures.viewBinding = true
    android.buildFeatures.buildConfig = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    lint {
        baseline = file("lint-baseline.xml")
        disable += "NullSafeMutableLiveData"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    api(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    api(project(mapOf("path" to ":cmonet")))
    api(libs.room.runtime)
    api(libs.room.ktx)
    kapt(libs.room.compiler)
    api(libs.datastore.preference)
    api(libs.dagger.hilt.android)
    kapt(libs.dagger.hilt.compiler)
    api(libs.asyncLayoutInflater)
    api(libs.glide.glide)
    kapt(libs.glide.compiler)
    api(libs.compressor)
    api(libs.kotlin.stdlib)
    api(libs.kotlin.coroutine.android)
    api(libs.kotlin.coroutine.core)
    api(libs.appcompat)
    api(libs.material)
    api(libs.fragment.ktx)
    api(libs.activity.ktx)
    api(libs.lifecycle.runtime.ktx)
    api(libs.lifecycle.livedata.ktx)
    api(libs.lifecycle.viewModel.ktx)
    api(libs.viewpager2)
    api(libs.recyclerview)
    api(libs.constraintLayout)
    api(libs.exifInterface)
    api(libs.palette.ktx)
    api(libs.profieinstaller)
    api(libs.colorpicker)
    api(libs.blurview)
    api(libs.zxing.core)

    implementation("com.github.royt93:AdmobApplovinWrapper:1.1.5")
    implementation(libs.konfetti.xml)
    implementation(libs.shimmer)
    api("com.jakewharton:process-phoenix:3.0.0")
    implementation("com.google.android.play:review:2.0.2")
    implementation("com.google.android.play:review-ktx:2.0.2")
//    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

    // unit test (JVM + Robolectric)
    testImplementation(libs.test.junit)
    testImplementation(libs.test.truth)
    testImplementation(libs.test.coroutines)
    testImplementation(libs.test.robolectric)
    testImplementation(libs.test.arch.core)
    testImplementation(libs.test.mockk)
    testImplementation(libs.test.core)

    // instrumentation test (androidTest)
    androidTestImplementation(libs.test.core)
    androidTestImplementation(libs.test.rules)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.test.espresso.core)
    androidTestImplementation(libs.test.truth)
    androidTestImplementation(libs.test.coroutines)
    androidTestImplementation(libs.test.room)
}
