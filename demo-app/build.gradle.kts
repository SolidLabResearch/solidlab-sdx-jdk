plugins {
    kotlin("jvm") version "1.7.20"
    id("be.solidlab.sdx-plugin") version "0.1"
}

dependencies {
    implementation("be.solidlab.sdx:solid-sdx-client:0.1")
}

repositories {
    mavenCentral()
    mavenLocal()
}

group = "be.solidlab.sdx"
version = "0.1"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_17.toString()
    kotlinOptions.javaParameters = true
}

sdx {
    importShapesFromURL.addAll(
        "http://localhost:3000/shapes/contact-SHACL.ttl"
    )
    packageName.set("be.solid.sdx.demo.queries")
}
