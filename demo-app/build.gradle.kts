plugins {
    kotlin("jvm") version "1.7.20"
    id("be.solidlab.sdx-plugin") version "0.1"
}

dependencies {
    implementation("com.apollographql.apollo3:apollo-runtime:3.7.4")
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
        "https://data.vlaanderen.be/shacl/adresregister-SHACL.ttl"
    )
    packageName.set("be.solid.sdx.demo.queries")
}
