import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.kapt")
    id("dagger.hilt.android.plugin")
}

// Toàn bộ secret (keystore, ad ID, SDK key, VIP secret, test-device hash) nằm ở 1 file duy nhất
// ngoài repo: myKeyStore/com.mckimquyen.watermark/app.properties (repo GitHub private royt93/myKeyStore).
// Ưu tiên env WATERMARK_PRIVATE_CONFIG_DIR, fallback đường dẫn local trên máy dev.
val privateConfigDir: File = File(
    System.getenv("WATERMARK_PRIVATE_CONFIG_DIR")
        ?: "${System.getProperty("user.home")}/AndroidStudioProjects/@mckimquyen/myKeyStore/com.mckimquyen.watermark"
)
val privateProps = Properties().apply {
    val f = File(privateConfigDir, "app.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val releaseRequested = gradle.startParameter.taskNames.any {
    it.contains("release", ignoreCase = true) || it.contains("bundle", ignoreCase = true)
}

// Release thiếu key → fail ngay, không bao giờ rơi về ID rỗng/test. Debug thiếu key → placeholder vô hại.
fun priv(key: String, debugFallback: String = ""): String =
    privateProps.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: if (releaseRequested) {
            throw GradleException("Thiếu '$key' trong ${privateConfigDir}/app.properties (build release bắt buộc)")
        } else {
            debugFallback
        }

android {
    compileSdk = 37
//    buildToolsVersion = "37.0.0"
    namespace = "com.mckimquyen.watermark"

    defaultConfig {
        applicationId = "com.mckimquyen.watermark"
        minSdk = 24
        targetSdk = 37
        versionCode = 20260929
        versionName = "2026.09.29"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        setProperty("archivesBaseName", "$applicationId-v$versionName($versionCode)")

        buildConfigField("String", "APPLOVIN_SDK_KEY", "\"${priv("APPLOVIN_SDK_KEY")}\"")
        buildConfigField("String", "APPLOVIN_BANNER_ID", "\"${priv("APPLOVIN_BANNER_ID")}\"")
        buildConfigField("String", "APPLOVIN_INTERSTITIAL_ID", "\"${priv("APPLOVIN_INTERSTITIAL_ID")}\"")
        buildConfigField("String", "APPLOVIN_APP_OPEN_ID", "\"${priv("APPLOVIN_APP_OPEN_ID")}\"")
        buildConfigField("String", "APPLOVIN_REWARDED_ID", "\"${priv("APPLOVIN_REWARDED_ID")}\"")
        buildConfigField("String", "VIP_KEY_SECRET", "\"${priv("VIP_KEY_SECRET", "debug-only-vip-secret-placeholder")}\"")
        buildConfigField("String", "VIP_TOKEN_PUBLIC_KEY", "\"${priv("VIP_TOKEN_PUBLIC_KEY")}\"")
        buildConfigField("String", "VIP_LEGACY_30D_CODE", "\"${priv("VIP_LEGACY_30D_CODE", "debug-only-30d")}\"")
        buildConfigField("String", "VIP_LEGACY_3D_CODE", "\"${priv("VIP_LEGACY_3D_CODE", "debug-only-3d")}\"")
        // Hash máy test (CHỈ THÊM, không xoá) — áp cho CẢ debug lẫn release, xem SplashActivity.
        buildConfigField("String", "ADMOB_TEST_DEVICE_IDS", "\"${priv("ADMOB_TEST_DEVICE_IDS")}\"")
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${priv("PRIVACY_POLICY_URL")}\"")
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
            privateProps.getProperty("KS_ALIAS")?.let { keyAlias = it }
            privateProps.getProperty("KS_KEY_PASSWORD")?.let { keyPassword = it }
            privateProps.getProperty("KEYSTORE_FILE")?.let { storeFile = File(privateConfigDir, it) }
            privateProps.getProperty("KS_STORE_PASSWORD")?.let { storePassword = it }
        }
    }
    buildTypes {
        val debug by getting {
//            applicationIdSuffix = ".debug"
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713" // Google sample app id
            buildConfigField("Boolean", "IS_ENABLE_ADMOB", "true")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "ADMOB_APP_OPEN_ID", "\"ca-app-pub-3940256099942544/9257395921\"")
            buildConfigField("String", "ADMOB_REWARDED_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
        }

        val release by getting {
            manifestPlaceholders["admobAppId"] = priv("ADMOB_APP_ID")
            buildConfigField("Boolean", "IS_ENABLE_ADMOB", "true")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"${priv("ADMOB_BANNER_ID")}\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${priv("ADMOB_INTERSTITIAL_ID")}\"")
            buildConfigField("String", "ADMOB_APP_OPEN_ID", "\"${priv("ADMOB_APP_OPEN_ID")}\"")
            buildConfigField("String", "ADMOB_REWARDED_ID", "\"${priv("ADMOB_REWARDED_ID")}\"")

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
        // SelectedPhotoAccess không tôn trọng tools:ignore (đã thử cả <uses-permission> lẫn root
        // <manifest>, vẫn báo) — tắt ở cấp module tới khi triển khai IDEA-19 (luồng "Chọn thêm
        // ảnh" khi quyền ảnh Android 14+ bị giới hạn).
        disable += "SelectedPhotoAccess"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            // ponytail: >30 class Robolectric dồn chung 1 JVM fork mặc định (Xmx 512m) từng
            // treo thật (không crash, không log lỗi — chỉ đứng im) khi chạy full suite nhiều
            // lần trong phiên làm việc dài. Tăng heap + tách fork định kỳ để tránh tích luỹ
            // SDK cache/GC pressure qua nhiều class. Nâng thêm nếu vẫn treo khi thêm test mới.
            // BUG-FLAKY-2026-09-30: đã thử forkEvery=10 cho SaveImageBSDialogFragment*RoboTest hay
            // fail rải rác khi chạy full suite — KHÔNG cải thiện (đo 3 lần: vẫn fail 2/3, tốn thêm
            // ~40% thời gian). Root cause thật KHÔNG phải fork/heap: 3 test đó dựng MainActivity
            // qua Hilt thật (fragment ép kiểu requireActivity() as MainActivity) nên đụng DataStore
            // singleton `context.userDataStore`/`waterMarkDataStore` dùng chung xuyên JVM fork —
            // nếu 1 test khác bị Robolectric huỷ sandbox giữa lúc `edit{}` dở dang, Mutex ghi khoá
            // VĨNH VIỄN (xem testutil/TestDataStores.kt). Đã fix bằng
            // di/TestDataStoreModule.kt (`@TestInstallIn` cô lập DataStore cho test Hilt) — verify
            // 7 lần chạy full suite liên tiếp không còn fail. forkEvery vẫn giữ 25 vì không phải
            // nguyên nhân.
            all {
                it.maxHeapSize = "3g"
                it.forkEvery = 25
            }
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
    // ENH-01: batch export qua WorkManager, Hilt Worker (@HiltWorker/@AssistedInject).
    api(libs.work.runtime.ktx)
    api(libs.hilt.work)
    kapt(libs.hilt.work.compiler)
    // WorkManager public API (ListenableWorker.startWork()) trả ListenableFuture — cần guava thật
    // trên COMPILE classpath (không chỉ shim listenablefuture rỗng) để Kotlin resolve type đầy đủ
    // khi subclass CoroutineWorker. Bản thật của guava vốn đã có ở runtime qua lib khác (AdMob/Play
    // Review) nhưng chỉ transitive runtime, không lộ ra compile classpath — khai báo thẳng ở đây.
    api("com.google.guava:guava:33.3.1-android")
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
    api(libs.documentfile)
    api(libs.palette.ktx)
    api(libs.profieinstaller)
    api(libs.colorpicker)
    api(libs.blurview)
    api(libs.zxing.core)
    // IDEA-01: Face Detection on-device (bundled model, không cần Play Services tải thêm).
    implementation(libs.mlkit.face.detection)
    // IDEA-14: Text Recognition on-device (bundled model) — phát hiện email/SĐT nhạy cảm.
    implementation(libs.mlkit.text.recognition)

    implementation("com.github.royt93:AdmobApplovinWrapper:1.8.5")
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
    testImplementation(libs.test.work)
    // BUG-FLAKY-2026-09-30: @TestInstallIn override DataStoreModule (DataStore cô lập cho test
    // Hilt thật qua MainActivity) — xem di/TestDataStoreModule.kt.
    testImplementation(libs.dagger.hilt.android.testing)
    kaptTest(libs.dagger.hilt.compiler)

    // instrumentation test (androidTest)
    androidTestImplementation(libs.test.core)
    androidTestImplementation(libs.test.rules)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.test.espresso.core)
    androidTestImplementation(libs.test.truth)
    androidTestImplementation(libs.test.coroutines)
    androidTestImplementation(libs.test.room)
    // IDEA-12 BUG-45 follow-up: cần TestListenableWorkerBuilder dựng BatchExportWorker thật trên
    // device (decode bitmap thật, khác Robolectric) — cùng artifact đã dùng ở testImplementation.
    androidTestImplementation(libs.test.work)
}
