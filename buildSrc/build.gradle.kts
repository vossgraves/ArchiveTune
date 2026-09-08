plugins {
    kotlin("jvm") version "2.4.0"
}

repositories {
    google()
    // GCS mirror of Maven Central, declared BEFORE mavenCentral() for the same
    // reason as in settings.gradle.kts: repo.maven.apache.org rate-limits with
    // HTTP 429, which Gradle treats as fatal rather than falling through to the
    // next repository. buildSrc is a separate build with its own repository list,
    // so it did not inherit that protection and failed on a cold cache.
    maven {
        name = "GcsCentral"
        setUrl("https://maven-central.storage-download.googleapis.com/maven2/")
    }
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(gradleApi())
    implementation(localGroovy())
    implementation("org.apache.xmlgraphics:batik-all:1.19")
}
