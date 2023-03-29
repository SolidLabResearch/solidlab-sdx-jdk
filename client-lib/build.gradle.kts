plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

val vertxVersion = "4.4.0"

dependencies {
    api(project(":commons"))
    implementation("org.apache.jena:jena-shacl:4.7.0")
    implementation("com.graphql-java:graphql-java:20.0")
    implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
    implementation("io.vertx:vertx-core")
    implementation("io.vertx:vertx-web-client")
    implementation("io.vertx:vertx-auth-oauth2")
    implementation("com.nimbusds:nimbus-jose-jwt:9.31")
    implementation("io.vertx:vertx-lang-kotlin")
    implementation("ch.qos.logback:logback-classic:1.4.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.7.20")
    api("com.apollographql.apollo3:apollo-runtime:3.7.4")
    testImplementation(kotlin("test"))
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
            artifactId = "client-lib"
            version = "0.1"

            from(components["java"])
        }
    }
}
