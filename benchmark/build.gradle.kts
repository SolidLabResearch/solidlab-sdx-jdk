import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    id("be.solidlab.sdx-plugin") version "1.0-SNAPSHOT"
    `maven-publish`
    id("me.champeau.jmh") version "0.6.8"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

dependencies {
    jmh(project(":client-lib"))
    implementation(project(":client-lib"))
    implementation(platform("io.vertx:vertx-stack-depchain:4.4.0"))
    implementation("io.vertx:vertx-core")
    implementation("io.vertx:vertx-web-client")
    implementation("io.vertx:vertx-lang-kotlin")
    implementation("org.apache.jena:jena-shacl:4.7.0")
    implementation("com.apollographql.apollo3:apollo-rx2-support:3.8.1")
    implementation("org.openjdk.jmh:jmh-core:1.35")
    implementation("org.openjdk.jmh:jmh-generator-annprocess:1.36")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.36")
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
    packageName.set("be.solid.sdx.benchmark.queries")
}



jmh {

}

tasks{
    named<ShadowJar>("shadowJar") {
        archiveBaseName.set("benchmark")
        mergeServiceFiles()
        manifest {
            attributes(mapOf("Main-Class" to "test.BenchRunner"))
        }
    }
}
