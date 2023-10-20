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
rootProject.children.forEach {
    it.name = (if(it.name == "gradle-plugin") "sdx-plugin" else it.name)
}



