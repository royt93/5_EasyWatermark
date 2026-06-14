rootProject.name = "Watermark_Creator"
include(":app")
include(":cmonet")
// include(":baseBenchmarks")
// include(":macrobenchmark")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            // plugins
            val kotlinVersion = "2.1.0"
            library("dagger-hilt-plugin", "com.google.dagger:hilt-android-gradle-plugin:2.57.2")
            library("tools-gradle", "com.android.tools.build:gradle:8.7.2")
            library("kotlin-plugin", "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
            library("ktlint-gradle", "org.jlleitschuh.gradle:ktlint-gradle:11.3.1")

            // kotlin libs
            library("kotlin-stdlib", "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
            library("kotlin-coroutine-android", "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
            library("kotlin-coroutine-core", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

            // android platforms libs
            library("fragment-ktx", "androidx.fragment:fragment-ktx:1.8.6")
            library("activity-ktx", "androidx.activity:activity-ktx:1.9.3")
            library("lifecycle-runtime-ktx", "androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
            library("lifecycle-livedata-ktx", "androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
            library("lifecycle-viewModel-ktx", "androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
            library("core-ktx", "androidx.core:core-ktx:1.15.0")
            library("appcompat", "androidx.appcompat:appcompat:1.7.0")
            library("material", "com.google.android.material:material:1.12.0")
            val roomVersion = "2.8.4"
            library("room-runtime", "androidx.room:room-runtime:$roomVersion")
            library("room-ktx", "androidx.room:room-ktx:$roomVersion")
            library("room-compiler", "androidx.room:room-compiler:$roomVersion")
            library("datastore-preference", "androidx.datastore:datastore-preferences:1.1.1")
            library("asyncLayoutInflater", "androidx.asynclayoutinflater:asynclayoutinflater:1.0.0")
            library("viewpager2", "androidx.viewpager2:viewpager2:1.1.0")
            library("recyclerview", "androidx.recyclerview:recyclerview:1.3.2")
            library("constraintLayout", "androidx.constraintlayout:constraintlayout:2.2.0")
            library("exifInterface", "androidx.exifinterface:exifinterface:1.3.5")
            library("palette-ktx", "androidx.palette:palette-ktx:1.0.0")
            library("blurview", "com.github.Dimezis:BlurView:version-2.0.3")

            // third party libs
            val daggerVersion = "2.57.2"
            library("dagger-hilt-android", "com.google.dagger:hilt-android:$daggerVersion")
            library("dagger-hilt-compiler", "com.google.dagger:hilt-compiler:$daggerVersion")

            val glideVersion = "4.16.0"
            library("glide-glide", "com.github.bumptech.glide:glide:$glideVersion")
            library("glide-compiler", "com.github.bumptech.glide:compiler:$glideVersion")

            library("compressor", "id.zelory:compressor:3.0.1")
            library("colorpicker", "com.github.skydoves:colorpickerview:2.2.3")

            // QR code
            library("zxing-core", "com.google.zxing:core:3.5.3")

            // VIP screen UX: confetti + shimmer
            library("konfetti-xml", "nl.dionsegijn:konfetti-xml:2.0.5")
            library("shimmer", "com.facebook.shimmer:shimmer:0.5.0")

            // benchmark && test libs
            library("benchmark", "androidx.benchmark:benchmark-macro-junit4:1.1.1")
            library("profieinstaller", "androidx.profileinstaller:profileinstaller:1.2.2")

            // unit test (JVM + Robolectric)
            library("test-junit", "junit:junit:4.13.2")
            library("test-truth", "com.google.truth:truth:1.4.4")
            library("test-coroutines", "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            library("test-robolectric", "org.robolectric:robolectric:4.14.1")
            library("test-arch-core", "androidx.arch.core:core-testing:2.2.0")
            library("test-mockk", "io.mockk:mockk:1.13.13")

            // instrumentation test (androidTest)
            library("test-core", "androidx.test:core:1.6.1")
            library("test-rules", "androidx.test:rules:1.6.1")
            library("test-runner", "androidx.test:runner:1.6.2")
            library("test-ext-junit", "androidx.test.ext:junit:1.2.1")
            library("test-espresso-core", "androidx.test.espresso:espresso-core:3.6.1")
            library("test-room", "androidx.room:room-testing:2.6.1")
        }
    }
}
