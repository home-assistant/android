object Config {

    object Plugin {
        const val android = "com.android.tools.build:gradle:4.2.1"
        const val kotlin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Dependency.Kotlin.version}"
        const val google = "com.google.gms:google-services:4.3.8"
        const val appDistribution = "com.google.firebase:firebase-appdistribution-gradle:2.1.2"
        const val androidJunit5 = "de.mannodermaus.gradle.plugins:android-junit5:1.6.2.0"
        const val gpp = "com.github.triplet.gradle:play-publisher:3.5.0-SNAPSHOT"
    }

    object Android {
        const val compileSdk = 30
        const val minSdk = 21
        const val minSdkWear = 23
        const val targetSdk = 30
        const val ndk = "21.3.6528147"
    }

    object Dependency {
        object Kotlin {
            const val version = "1.5.0"
            const val core = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${version}"
            const val reflect = "org.jetbrains.kotlin:kotlin-reflect:${version}"

            private const val coroutinesVersion = "1.4.3"
            const val coroutines = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${coroutinesVersion}"
            const val coroutinesPlayServices = "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:${coroutinesVersion}"
            const val coroutinesAndroid = "org.jetbrains.kotlinx:kotlinx-coroutines-android:${coroutinesVersion}"
            const val coroutinesTest = "org.jetbrains.kotlinx:kotlinx-coroutines-test:${coroutinesVersion}"
        }

        object Google {
            private const val daggerVersion = "2.35.1"
            const val dagger = "com.google.dagger:dagger:${daggerVersion}"
            const val daggerCompiler = "com.google.dagger:dagger-compiler:${daggerVersion}"

            const val material = "com.google.android.material:material:1.3.0"

            const val wearableSupport = "com.google.android.support:wearable:2.8.1"
            const val wearable = "com.google.android.wearable:wearable:2.8.1"
        }

        object AndroidX {

            const val webKit = "androidx.webkit:webkit:1.4.0"
            const val appcompat = "androidx.appcompat:appcompat:1.2.0"
            const val lifecycle = "androidx.lifecycle:lifecycle-runtime-ktx:2.3.1"
            const val recyclerview = "androidx.recyclerview:recyclerview:1.2.0"
            const val constraintlayout = "androidx.constraintlayout:constraintlayout:2.0.4"
            const val preference = "androidx.preference:preference-ktx:1.1.1"

            const val wear = "androidx.wear:wear:1.1.0"

            const val navigationFragment = "androidx.navigation:navigation-fragment-ktx:2.3.5"
            const val navigationUi = "androidx.navigation:navigation-ui-ktx:2.3.5"

            const val workManager = "androidx.work:work-runtime-ktx:2.5.0"
            const val biometric = "androidx.biometric:biometric:1.1.0"

            private const val roomVersion = "2.2.6"
            const val roomRuntime = "androidx.room:room-runtime:${roomVersion}"
            const val roomKtx = "androidx.room:room-ktx:${roomVersion}"
            const val roomCompiler = "androidx.room:room-compiler:${roomVersion}"
        }

        object Play {
            const val location = "com.google.android.gms:play-services-location:18.0.0"
        }

        object Firebase {
            const val core = "com.google.firebase:firebase-core:19.0.0"
            const val iid = "com.google.firebase:firebase-iid:21.1.0"
            const val messaging = "com.google.firebase:firebase-messaging:22.0.0"
        }

        object Square {
            private const val retrofitVersion = "2.9.0"
            const val retrofit = "com.squareup.retrofit2:retrofit:${retrofitVersion}"
            const val retrofitJacksonConverter = "com.squareup.retrofit2:converter-jackson:${retrofitVersion}"

            private const val okhttpVersion = "4.9.1"
            const val okhttp = "com.squareup.okhttp3:okhttp:$okhttpVersion"
            const val okhttpInterceptor = "com.squareup.okhttp3:logging-interceptor:$okhttpVersion"
            const val okhttpMockServer = "com.squareup.okhttp3:mockwebserver:$okhttpVersion"

            private const val picassoVersion = "2.8"
            const val picasso = "com.squareup.picasso:picasso:${picassoVersion}"
        }

        object Testing {
            private const val assertJVersion = "3.13.2"
            const val assertJ = "org.assertj:assertj-core:${assertJVersion}"

            private const val mockkVersion = "1.9.3"
            const val mockk = "io.mockk:mockk:${mockkVersion}"

            const val spek2Version = "2.0.8"
            const val spek2Jvm = "org.spekframework.spek2:spek-dsl-jvm:${spek2Version}"
            const val spek2JUnit = "org.spekframework.spek2:spek-runner-junit5:${spek2Version}"
        }

        object Misc {
            const val sentry = "io.sentry:sentry-android:4.3.0"
            const val jackson = "com.fasterxml.jackson.module:jackson-module-kotlin:2.12.3"
            const val blurView = "com.eightbitlab:blurview:1.6.6"
            const val iconDialog = "com.maltaisn:icondialog:3.3.0"
            const val iconDialogMaterial = "com.maltaisn:iconpack-community-material:5.3.45"
            const val emoji = "com.vdurmont:emoji-java:5.1.1"
            const val exoCore = "com.google.android.exoplayer:exoplayer-core:2.14.1"
            const val exoHls = "com.google.android.exoplayer:exoplayer-hls:2.14.1"
            const val exoUi = "com.google.android.exoplayer:exoplayer-ui:2.14.1"
            const val exoCronet = "com.google.android.exoplayer:extension-cronet:2.14.1"
            const val altBeacon =  "org.altbeacon:android-beacon-library:2+"
        }
    }

}
