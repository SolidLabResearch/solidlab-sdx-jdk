plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
}

dependencies {
    implementation(project(":commons"))
    implementation(kotlin("stdlib", "1.7.20"))
    implementation("org.apache.jena:jena-shacl:4.7.0")
    implementation("com.graphql-java:graphql-java:20.0")
    api("com.apollographql.apollo3:com.apollographql.apollo3.gradle.plugin:3.7.4")
    implementation("com.apollographql.apollo3.external:com.apollographql.apollo3.external.gradle.plugin:3.7.4")
    testImplementation(kotlin("test"))
    implementation("be.solidlab:shapeshift:0.1")
}

repositories {
    mavenCentral()
    mavenLocal()
}

gradlePlugin {
    plugins {
        create("sdxPlugin") {
            id = "be.solidlab.sdx-plugin"
            implementationClass = "be.solidlab.sdx.gradle.plugin.SolidGradlePlugin"
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_17.toString()
    kotlinOptions.javaParameters = true
}

tasks.test {
    useJUnitPlatform()
}

group = "be.solidlab.sdx"
version = "1.0-SNAPSHOT"

publishing {
    repositories {
        mavenLocal()
    }
}
