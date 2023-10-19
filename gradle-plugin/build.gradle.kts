plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
    signing
    id("org.jetbrains.dokka") version "1.9.0"
}

dependencies {
    implementation(project(":commons"))
    implementation(kotlin("stdlib", "1.7.20"))
    implementation("org.apache.jena:jena-shacl:4.7.0")
    implementation("com.graphql-java:graphql-java:20.0")
    api("com.apollographql.apollo3:com.apollographql.apollo3.gradle.plugin:3.7.4")
    implementation("com.apollographql.apollo3.external:com.apollographql.apollo3.external.gradle.plugin:3.7.4")
    testImplementation(kotlin("test"))
    implementation("be.ugent.solidlab:shapeshift:0.0.1")
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("sdxPlugin") {
            id = "be.ugent.solidlab.sdx-plugin"
            implementationClass = "be.ugent.solidlab.sdx.gradle.plugin.SolidGradlePlugin"
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



signing {
    val signingKey = providers
        .environmentVariable("GPG_SIGNING_KEY")
        .forUseAtConfigurationTime()
    val signingPassphrase = providers
        .environmentVariable("GPG_SIGNING_PASSPHRASE")
        .forUseAtConfigurationTime()
    if (signingKey.isPresent && signingPassphrase.isPresent) {
        useInMemoryPgpKeys(signingKey.get(), signingPassphrase.get())
        val extension = extensions
            .getByName("publishing") as PublishingExtension
        sign(extension.publications)
    }
}

group = "be.ugent.solidlab"

object Meta {
    const val desc = "Gradle Plugin to allow easy Solid Development"
    const val license = "MIT License"
    const val githubRepo = "SolidLabResearch/solidlab-sdx-jdk"
    const val release = "https://oss.sonatype.org/service/local/"
    const val snapshot = "https://oss.sonatype.org/content/repositories/snapshots/"
}

publishing {


    repositories {
        maven {
            name = "Sonatype"
            val releasesRepoUrl = Meta.release
            val snapshotsRepoUrl = Meta.snapshot
            url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
            credentials {
                val osshrUsername = providers.environmentVariable("OSSRH_USERNAME").forUseAtConfigurationTime()
                val osshrPassword = providers.environmentVariable("OSSRH_PASSWORD").forUseAtConfigurationTime()
                if(osshrUsername.isPresent && osshrPassword.isPresent){
                    username = osshrUsername.get()
                    password = osshrPassword.get()
                }

            }
        }
    }
}
