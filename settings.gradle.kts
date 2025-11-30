import java.net.URL
import java.nio.file.Files

/**
 * ──────────────────────────────────────────────
 *  Root Gradle Settings (Dependency + Plugin Mgmt)
 * ──────────────────────────────────────────────
 */

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    // Global Kotlin plugin version for all modules
    plugins {
        kotlin("jvm") version "2.2.10"
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }

    /**
     * Loads a remote libs.versions.toml from LeyCM’s repository.
     * This keeps version catalogs consistent across projects.
     */
    versionCatalogs {
        create("libs") {
            val remoteUrl = "https://raw.githubusercontent.com/leycm/leycm/refs/heads/main/files/libs.version.toml"
            val localFile = file("$rootDir/.gradle/tmp-libs.versions.toml")

            println("[✓] Loading global libs.versions.toml ...")
            localFile.parentFile.mkdirs()
            if (localFile.exists()) localFile.delete()

            @Suppress("DEPRECATION")
            URL(remoteUrl).openStream().use { input ->
                Files.copy(input, localFile.toPath())
            }

            from(files(localFile))
        }
    }
}

// ─────────────────────────────
//  Project Includes
// ─────────────────────────────
rootProject.name = "ley-template"

include("api", "common")

project(":api").projectDir = file("tmp-api")
project(":common").projectDir = file("tmp-common")
