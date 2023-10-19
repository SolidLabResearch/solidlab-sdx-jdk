plugins {
    kotlin("jvm") version "1.7.20" apply false
    `maven-publish`
    signing
    id("org.jetbrains.dokka") version "1.9.0"
    id("io.github.gradle-nexus.publish-plugin") version "1.0.0"
}
