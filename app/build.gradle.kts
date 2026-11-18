import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.imageio.ImageIO

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

fun quotedBuildConfig(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
fun validHttpBaseUrl(value: String?): String? {
    val trimmed = value?.trim()?.trimEnd('/').orEmpty()
    return trimmed.takeIf {
        it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
    }
}

val defaultBackendBaseUrl = "https://81.71.1.11"
val feedbackBaseUrl = validHttpBaseUrl(
    providers.gradleProperty("YUJIAN_FEEDBACK_BASE_URL")
        .orElse(providers.environmentVariable("YUJIAN_FEEDBACK_BASE_URL")).orNull,
) ?: defaultBackendBaseUrl
val fishKnowledgeBaseUrl = validHttpBaseUrl(
    providers.gradleProperty("YUJIAN_FISH_KNOWLEDGE_BASE_URL")
        .orElse(providers.environmentVariable("YUJIAN_FISH_KNOWLEDGE_BASE_URL")).orNull,
) ?: feedbackBaseUrl
val userApiBaseUrl = validHttpBaseUrl(
    providers.gradleProperty("YUJIAN_API_BASE_URL")
        .orElse(providers.environmentVariable("YUJIAN_API_BASE_URL")).orNull,
) ?: feedbackBaseUrl
val feedbackIngestKey = providers.gradleProperty("YUJIAN_FEEDBACK_INGEST_KEY")
    .orElse(providers.environmentVariable("YUJIAN_FEEDBACK_INGEST_KEY")).orElse("").get()

android {
    namespace = "com.yujian.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yujian.ai.uiv2"
        val sourceRevision = providers.environmentVariable("SOURCE_SHA")
            .orElse(providers.environmentVariable("BUILD_SHA"))
            .orElse(providers.environmentVariable("GITHUB_SHA"))
            .orElse("LOCAL_UNVERIFIED")
        val buildTimestampUtc = providers.environmentVariable("BUILD_TIMESTAMP_UTC")
            .orElse(Instant.now().truncatedTo(ChronoUnit.DAYS).toString())
        buildConfigField("String", "SOURCE_REVISION", quotedBuildConfig(sourceRevision.get()))
        buildConfigField("String", "BUILD_TIMESTAMP_UTC", quotedBuildConfig(buildTimestampUtc.get()))
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "2.3.3-orb-wake-v1.1-20261103"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "FEEDBACK_BASE_URL", quotedBuildConfig(feedbackBaseUrl))
        buildConfigField("String", "FEEDBACK_INGEST_KEY", quotedBuildConfig(feedbackIngestKey))
        buildConfigField("String", "FISH_KNOWLEDGE_BASE_URL", quotedBuildConfig(fishKnowledgeBaseUrl))
        buildConfigField("String", "USER_API_BASE_URL", quotedBuildConfig(userApiBaseUrl))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = true
    }
}

secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"
    ignoreList.add("YUJIAN_.*")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-video:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
    implementation("com.google.android.gms:play-services-base:18.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.29.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
