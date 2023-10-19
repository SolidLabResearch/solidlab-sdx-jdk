plugins {
    kotlin("jvm")
    `java-gradle-plugin`
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
    mavenLocal()
}

gradlePlugin {
    plugins {
        create("sdxPlugin") {
            id = "be.ugent.solidlab.sdx-plugin"
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

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(kotlin.sourceSets.main.get().kotlin)
}

val javadocJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles Javadoc JAR"
    archiveClassifier.set("javadoc")
    from(tasks.named("dokkaHtml"))
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

group = "be.ugent.solidlab.sdx"

object Meta {
    const val desc = "Gradle Plugin to allow easy Solid Development"
    const val license = "MIT License"
    const val githubRepo = "SolidLabResearch/solidlab-sdx-jdk"
    const val release = "https://oss.sonatype.org/service/local/"
    const val snapshot = "https://oss.sonatype.org/content/repositories/snapshots/"
}

publishing {
    publications {
        create<MavenPublication>("sdx-plugin") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            from(components["kotlin"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])

            pom {
                name.set(project.name)
                description.set(Meta.desc)
                url.set("https://github.com/${Meta.githubRepo}")

                licenses {
                    license {
                        name.set(Meta.license)
                        url.set("http://www.opensource.org/licenses/mit-license.php")
                    }
                }

                developers {
                    developer {
                        id.set("koluyten")
                        name.set("Koen Luyten")
                        organization.set("IDLAB")
                        organizationUrl.set("https://www.ugent.be/ea/idlab/en")
                    }
                    developer {
                        id.set("wkerckho")
                        name.set("Wannes Kerckhove")
                        organization.set("IDLAB")
                        organizationUrl.set("https://www.ugent.be/ea/idlab/en")
                    }
                }

                scm {
                    url.set(
                        "https://github.com/${Meta.githubRepo}.git"
                    )
                    connection.set(
                        "scm:git:git://github.com/${Meta.githubRepo}.git"
                    )
                    developerConnection.set(
                        "scm:git:git://github.com/${Meta.githubRepo}.git"
                    )
                }
                issueManagement {
                    url.set("https://github.com/${Meta.githubRepo}/issues")
                }
            }
        }
    }
}
