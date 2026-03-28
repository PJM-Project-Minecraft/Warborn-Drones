pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            name = "NeoForged"
            url = uri("https://maven.neoforged.net/releases")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "wrbdrones"

// Используем composite build вместо include для отдельного проекта SuperbWarfare
includeBuild("SuperbWarfare") {
    dependencySubstitution {
        substitute(module("com.atsuishio:superbwarfare")).using(project(":"))
    }
}
