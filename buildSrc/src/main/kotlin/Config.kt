object Config {

    object Plugin {
        const val android = "com.android.tools.build:gradle:4.0.0"
        const val kotlin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Dependency.Kotlin.version}"
        const val google = "com.google.gms:google-services:4.3.3"
        const val appDistribution = "com.google.firebase:firebase-appdistribution-gradle:1.4.0"
        const val ktlint = "org.jlleitschuh.gradle:ktlint-gradle:9.2.1"
        const val playPublisher = "com.github.triplet.gradle:play-publisher:2.7.5"
        const val androidJunit5 = "de.mannodermaus.gradle.plugins:android-junit5:1.6.0.0"
        const val crashlytics = "com.google.firebase:firebase-crashlytics-gradle:2.1.1"
    }

    object Repository {
        const val gradle = "https://plugins.gradle.org/m2/"
        const val lokalize = "https://maven.lokalise.co"
    }

    object Android {
        const val compileSdk = 29
        const val minSdk = 21
        const val targetSdk = 29
        const val ndk = "21.3.6528147"
    }

    object Dependency {
        object Kotlin {
            const val version = "1.3.72"
            const val core = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${version}"

            private const val coroutinesVersion = "1.3.3"
            const val coroutines = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${coroutinesVersion}"
            const val coroutinesAndroid = "org.jetbrains.kotlinx:kotlinx-coroutines-android:${coroutinesVersion}"
            const val coroutinesTest = "org.jetbrains.kotlinx:kotlinx-coroutines-test:${coroutinesVersion}"
        }

        object Google {
            private const val daggerVersion = "2.27"
            const val dagger = "com.google.dagger:dagger:${daggerVersion}"
            const val daggerCompiler = "com.google.dagger:dagger-compiler:${daggerVersion}"

            const val material = "com.google.android.material:material:1.1.0"
        }

        object AndroidX {

            const val appcompat = "androidx.appcompat:appcompat:1.1.0"
            const val lifecycle = "androidx.lifecycle:lifecycle-runtime-ktx:2.2.0"
            const val recyclerview = "androidx.recyclerview:recyclerview:1.1.0"
            const val constraintlayout = "androidx.constraintlayout:constraintlayout:1.1.3"
            const val preference = "androidx.preference:preference-ktx:1.1.1"

            const val workManager = "androidx.work:work-runtime-ktx:2.3.4"
            const val biometric = "androidx.biometric:biometric:1.0.1"

            private const val roomVersion = "2.2.5"
            const val roomRuntime = "androidx.room:room-runtime:${roomVersion}"
            const val roomKtx = "androidx.room:room-ktx:${roomVersion}"
            const val roomCompiler = "androidx.room:room-compiler:${roomVersion}"
        }

        object Play {
            const val location = "com.google.android.gms:play-services-location:17.0.0"
        }

        object Firebase {
            const val core = "com.google.firebase:firebase-core:17.3.0"
            const val iid = "com.google.firebase:firebase-iid:20.1.5"
            const val messaging = "com.google.firebase:firebase-messaging:20.1.5"
            const val crashlytics = "com.google.firebase:firebase-crashlytics:17.0.1"
        }

        object Square {
            private const val retrofitVersion = "2.8.1"
            const val retrofit = "com.squareup.retrofit2:retrofit:${retrofitVersion}"
            const val retrofitJacksonConverter = "com.squareup.retrofit2:converter-jackson:${retrofitVersion}"

            private const val okhttpVersion = "4.5.0"
            const val okhttp = "com.squareup.okhttp3:okhttp:$okhttpVersion"
            const val okhttpInterceptor = "com.squareup.okhttp3:logging-interceptor:$okhttpVersion"
            const val okhttpMockServer = "com.squareup.okhttp3:mockwebserver:$okhttpVersion"
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
            const val lokalize = "com.lokalise.android:sdk:2.0.0-beta-5"
            const val jackson = "com.fasterxml.jackson.module:jackson-module-kotlin:2.10.1"
            const val threeTenBp = "org.threeten:threetenbp:1.4.0"
            const val threeTenAbp = "com.jakewharton.threetenabp:threetenabp:1.2.1"
            const val javaxInject = "javax.inject:javax.inject:1"
            const val blurView = "com.eightbitlab:blurview:1.6.3"
        }
    }

}
