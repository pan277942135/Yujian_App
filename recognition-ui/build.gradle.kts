import java.time.Instant
import java.time.temporal.ChronoUnit

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun quotedBuildConfig(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.yujian.ai.recognition.ui"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val sourceRevision = providers.environmentVariable("SOURCE_SHA")
            .orElse(providers.environmentVariable("BUILD_SHA"))
            .orElse(providers.environmentVariable("GITHUB_SHA"))
            .orElse("LOCAL_UNVERIFIED")
        val buildTimestampUtc = providers.environmentVariable("BUILD_TIMESTAMP_UTC")
            .orElse(Instant.now().truncatedTo(ChronoUnit.DAYS).toString())
        buildConfigField("String", "SOURCE_REVISION", quotedBuildConfig(sourceRevision.get()))
        buildConfigField("String", "BUILD_TIMESTAMP_UTC", quotedBuildConfig(buildTimestampUtc.get()))
        buildConfigField("int", "VERSION_CODE", "6")
        buildConfigField("String", "VERSION_NAME", quotedBuildConfig("2.3.3-orb-wake-v1.1-20261103"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    api(project(":application-core"))
    api(project(":recognition-domain"))
    api(project(":recognition-runtime"))
    api(project(":waterlight-renderer"))

    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
