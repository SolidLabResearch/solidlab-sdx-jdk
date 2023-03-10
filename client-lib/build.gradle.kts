plugins {
    kotlin("jvm") version "1.7.20"
    `java-library`
    `maven-publish`
}

val vertxVersion = "4.4.0"

dependencies {
    implementation("org.apache.jena:jena-shacl:4.7.0")
    implementation("com.graphql-java:graphql-java:20.0")
    implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
    implementation("io.vertx:vertx-core")
    implementation("io.vertx:vertx-web-client")
    implementation("io.vertx:vertx-lang-kotlin")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4")
    api("com.apollographql.apollo3:apollo-runtime:3.7.4")
    testImplementation(kotlin("test"))
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
    withSourcesJar()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_17.toString()
    kotlinOptions.javaParameters = true
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    repositories {
        mavenLocal()
    }
    publications {
        create<MavenPublication>("maven") {
            groupId = "be.solidlab.sdx"
            artifactId = "solid-sdx-client"
            version = "0.1"

            from(components["java"])
        }
    }
}
