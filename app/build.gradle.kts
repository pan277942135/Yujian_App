import java.awt.image.BufferedImage
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.math.sqrt

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun quotedBuildConfig(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
val feedbackBaseUrl = providers.gradleProperty("YUJIAN_FEEDBACK_BASE_URL")
    .orElse(providers.environmentVariable("YUJIAN_FEEDBACK_BASE_URL")).orElse("").get()
val fishKnowledgeBaseUrl = providers.gradleProperty("YUJIAN_FISH_KNOWLEDGE_BASE_URL")
    .orElse(providers.environmentVariable("YUJIAN_FISH_KNOWLEDGE_BASE_URL")).orElse(feedbackBaseUrl).get()
val userApiBaseUrl = providers.gradleProperty("YUJIAN_API_BASE_URL")
    .orElse(providers.environmentVariable("YUJIAN_API_BASE_URL")).orElse(feedbackBaseUrl).get()
val feedbackIngestKey = providers.gradleProperty("YUJIAN_FEEDBACK_INGEST_KEY")
    .orElse(providers.environmentVariable("YUJIAN_FEEDBACK_INGEST_KEY")).orElse("").get()

val emptyHomeFrozenHeroSource = rootProject.layout.projectDirectory.file(
    "design/pages/home/empty_home/source/frozen/Empty_Home_Final_Design_V2_normalized_1080x1920.png",
)
val emptyHomeGeneratedResDir = layout.buildDirectory.dir("generated/emptyHomeFrozenHeroRes")

val accountPrivacyBackgroundSource = rootProject.layout.projectDirectory.file(
    "design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png",
)
val accountPrivacyGeneratedResDir = layout.buildDirectory.dir("generated/accountPrivacyRes")

val generateAccountPrivacyBackground by tasks.registering {
    inputs.file(accountPrivacyBackgroundSource)
    outputs.dir(accountPrivacyGeneratedResDir)

    doLast {
        val sourceFile = accountPrivacyBackgroundSource.asFile
        require(sourceFile.isFile) { "Missing Account Privacy Morning Lake source: $sourceFile" }
        val sourceSha = MessageDigest.getInstance("SHA-256")
            .digest(sourceFile.readBytes())
            .joinToString("") { "%02x".format(it) }
        require(sourceSha == "5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7") {
            "Account Privacy Morning Lake SHA mismatch: $sourceSha"
        }
        val image = requireNotNull(ImageIO.read(sourceFile)) { "Unable to decode Account Privacy Morning Lake source" }
        require(image.width == 941 && image.height == 1672) {
            "Unexpected Account Privacy Morning Lake dimensions: " + image.width + "x" + image.height
        }
        val drawableDir = accountPrivacyGeneratedResDir.get().dir("drawable-nodpi").asFile
        drawableDir.mkdirs()
        sourceFile.copyTo(drawableDir.resolve("account_privacy_morning_lake.png"), overwrite = true)
    }
}

val generateEmptyHomeFrozenHero by tasks.registering {
    inputs.file(emptyHomeFrozenHeroSource)
    outputs.dir(emptyHomeGeneratedResDir)

    doLast {
        val sourceFile = emptyHomeFrozenHeroSource.asFile
        require(sourceFile.isFile) { "Missing Empty Home Frozen V2 source: $sourceFile" }

        val sourceSha = MessageDigest.getInstance("SHA-256")
            .digest(sourceFile.readBytes())
            .joinToString("") { "%02x".format(it) }
        require(sourceSha == "ebf96d310678b5fd47149cd8dadf3dd24cb00b2b83253876aa4cf9e89f954b77") {
            "Empty Home Frozen normalized source SHA mismatch: $sourceSha"
        }

        val sourceImage = requireNotNull(ImageIO.read(sourceFile)) {
            "Unable to decode Empty Home Frozen V2 source"
        }
        require(sourceImage.width == 1080 && sourceImage.height == 1920) {
            "Unexpected Empty Home Frozen V2 dimensions: " + sourceImage.width + "x" + sourceImage.height
        }

        val originX = 75
        val originY = 240
        val width = 606
        val height = 296
        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        fun distance(r: Int, g: Int, b: Int, tr: Int, tg: Int, tb: Int): Double {
            val dr = (r - tr).toDouble()
            val dg = (g - tg).toDouble()
            val db = (b - tb).toDouble()
            return sqrt(dr * dr + dg * dg + db * db)
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgb = sourceImage.getRGB(originX + x, originY + y)
                val r = rgb shr 16 and 0xFF
                val g = rgb shr 8 and 0xFF
                val b = rgb and 0xFF

                val navyDistance = distance(r, g, b, 18, 48, 72)
                val goldDistance = distance(r, g, b, 218, 160, 45)
                val navyAlpha = ((105.0 - navyDistance) * 4.5).coerceIn(0.0, 255.0)
                val goldAlpha = ((115.0 - goldDistance) * 4.0).coerceIn(0.0, 255.0)

                val validNavy = r < 115 && g < 135 && b < 150
                val validGold = r > 120 && g > 70 && b < 125 && (r - b) > 55
                val alpha = if (validNavy || validGold) {
                    maxOf(navyAlpha, goldAlpha).toInt()
                } else {
                    0
                }

                val outRgb = if (alpha == 0) {
                    0
                } else {
                    (alpha shl 24) or (r shl 16) or (g shl 8) or b
                }
                output.setRGB(x, y, outRgb)
            }
        }

        val drawableDir = emptyHomeGeneratedResDir.get().dir("drawable-nodpi").asFile
        drawableDir.mkdirs()
        val outputFile = drawableDir.resolve("empty_home_title_v2.png")
        check(ImageIO.write(output, "png", outputFile)) {
            "Unable to encode generated Empty Home Frozen Hero"
        }
    }
}

android {
    namespace = "com.yujian.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yujian.ai.uiv2"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "2.3.1-quality-gate-v1.1"
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
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = true
    }

    sourceSets.getByName("main").res.srcDir(emptyHomeGeneratedResDir)
    sourceSets.getByName("main").res.srcDir(accountPrivacyGeneratedResDir)
}

tasks.configureEach {
    if (name == "preBuild") {
        dependsOn(generateEmptyHomeFrozenHero)
        dependsOn(generateAccountPrivacyBackground)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
    implementation("com.google.android.gms:play-services-base:18.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
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
