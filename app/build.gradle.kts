plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun quotedBuildConfig(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
val feedbackBaseUrl = providers.gradleProperty("YUJIAN_FEEDBACK_BASE_URL")
    .orElse(providers.environmentVariable("YUJIAN_FEEDBACK_BASE_URL")).orElse("").get()
val feedbackIngestKey = providers.gradleProperty("YUJIAN_FEEDBACK_INGEST_KEY")
    .orElse(providers.environmentVariable("YUJIAN_FEEDBACK_INGEST_KEY")).orElse("").get()

android {
    namespace = "com.yujian.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yujian.ai.uiv2"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "2.2.0-m1-v0.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "FEEDBACK_BASE_URL", quotedBuildConfig(feedbackBaseUrl))
        buildConfigField("String", "FEEDBACK_INGEST_KEY", quotedBuildConfig(feedbackIngestKey))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.29.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
