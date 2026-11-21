plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
}

kotlin { jvmToolchain(17) }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(project(":recognition-domain"))
    testImplementation("junit:junit:4.13.2")
}
