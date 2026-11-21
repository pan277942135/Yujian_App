plugins {
    id("com.android.library")
}

fun quotedBuildConfig(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

fun validHttpBaseUrl(value: String?): String? {
    val trimmed = value?.trim()?.trimEnd('/').orEmpty()
    return trimmed.takeIf {
        it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
    }
}

fun setting(name: String) = providers.gradleProperty(name)
    .orElse(providers.environmentVariable(name)).orNull

val defaultBackendBaseUrl = "https://81.71.1.11"
val feedbackBaseUrl = validHttpBaseUrl(setting("YUJIAN_FEEDBACK_BASE_URL")) ?: defaultBackendBaseUrl
val knowledgeBaseUrl = validHttpBaseUrl(setting("YUJIAN_FISH_KNOWLEDGE_BASE_URL")) ?: feedbackBaseUrl
val userApiBaseUrl = validHttpBaseUrl(setting("YUJIAN_API_BASE_URL")) ?: feedbackBaseUrl
val feedbackIngestKey = setting("YUJIAN_FEEDBACK_INGEST_KEY").orEmpty()
val sourceRevision = providers.environmentVariable("SOURCE_SHA")
    .orElse(providers.environmentVariable("BUILD_SHA"))
    .orElse(providers.environmentVariable("GITHUB_SHA"))
    .orElse("LOCAL_UNVERIFIED")

android {
    namespace = "com.yujian.ai.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SOURCE_REVISION", quotedBuildConfig(sourceRevision.get()))
        buildConfigField("String", "FEEDBACK_BASE_URL", quotedBuildConfig(feedbackBaseUrl))
        buildConfigField("String", "FEEDBACK_INGEST_KEY", quotedBuildConfig(feedbackIngestKey))
        buildConfigField("String", "FISH_KNOWLEDGE_BASE_URL", quotedBuildConfig(knowledgeBaseUrl))
        buildConfigField("String", "USER_API_BASE_URL", quotedBuildConfig(userApiBaseUrl))
        buildConfigField("int", "VERSION_CODE", "6")
        buildConfigField("String", "VERSION_NAME", "\"2.3.3-orb-wake-v1.1-20261103\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
}

dependencies {
    api(project(":recognition-domain"))
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.29.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
