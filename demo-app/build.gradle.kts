plugins {
    kotlin("jvm")
    id("be.solidlab.sdx-plugin") version "1.0-SNAPSHOT"
    `maven-publish`
}

dependencies {
    implementation(project(":client-lib"))
}

repositories {
    mavenCentral()
    mavenLocal()
}

group = "be.solidlab.sdx"
version = "1.0-SNAPSHOT"

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
        "https://cloud.ilabt.imec.be/index.php/s/w3Hr9MENpM7fMBE/download/contact-SHACL.ttl",
//        "https://data.vlaanderen.be/shacl/adresregister-SHACL.ttl",
//        "https://data.vlaanderen.be/shacl/persoon-basis-SHACL.ttl"
    )
    packageName.set("be.solid.sdx.demo.queries")
}
