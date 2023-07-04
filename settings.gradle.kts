pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        mavenLocal()
    }
}
rootProject.name = "solid-sdx"
include("client-lib")
include("commons")
include("gradle-plugin")
include("demo-app")
include("benchmark")



