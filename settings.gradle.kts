import org.gradle.kotlin.dsl.support.serviceOf

include(":common", ":app")

rootProject.name = "home-assistant-android"

plugins {
    id("com.gradle.enterprise").version("3.7")
}

// It should be easier to read an environment variable here once github.com/gradle/configuration-cache/issues/211 is resolved.
val isCI = serviceOf<ProviderFactory>()
    .environmentVariable("CI")
    .forUseAtConfigurationTime().map { it == "true" }
    .getOrElse(false)

//gradleEnterprise {
//    buildScan {
//        termsOfServiceUrl = "https://gradle.com/terms-of-service"
//        termsOfServiceAgree = "yes"
//        publishAlwaysIf(isCI)
//        isUploadInBackground = !isCI
//    }
//}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/jcenter")
        maven("https://maven.aliyun.com/repository/public")
        //mavenCentral()
        //google()
        maven("https://jitpack.io")
    }
}
