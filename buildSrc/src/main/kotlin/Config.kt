object Config {
    const val version = "1.8.0"

    object Plugin {

    }

    object Android {
        const val compileSdk = 29
        const val minSdk = 21
        const val targetSdk = 29
    }

    object Dependency {
        object Kotlin {
            const val core = "1.3.72"
            const val coroutines = "1.3.3"
        }

        object Google {
            const val dagger = "2.27"
        }

        object AndroidX {
            const val appcompat = "1.1.0"
            const val lifecycle = "2.2.0"
            const val recyclerview = "1.1.0"
            const val constraintlayout = "1.1.3"
        }

        object Square {
            const val retrofit = "2.8.1"
            const val okhttp = "4.5.0"
        }

        object Testing {
            const val assertJ = "3.13.2"
            const val mockk = "1.9.3"
            const val spek2 = "2.0.8"
        }

        object Misc {
            const val jackson = "2.10.1"
            const val threeTenBp = "1.4.0"
            const val threeTenAbp = "1.2.1"
        }
    }

}