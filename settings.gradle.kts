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

// SuperbWarfare-форк лежит рядом с WRBDrones в общей папке "!Curseforge Mods"
val superbWarfareRoot = settingsDir.resolve("../SuperbWarfare-fork-PJM").normalize()

// Используем composite build вместо include для отдельного проекта SuperbWarfare
includeBuild(superbWarfareRoot) {
    dependencySubstitution {
        substitute(module("com.atsuishio:superbwarfare")).using(project(":"))
    }
}
