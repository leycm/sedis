import java.util.Properties

/**
 * ───────────────────────────────────────────────────────
 *  Root Gradle Build (Global Config + Subprojects)
 * ───────────────────────────────────────────────────────
 */

plugins {
    java
    `maven-publish`
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.lombok) apply false
}

//  ─────────────────────────────────────────────
//  Project Metadata
//  ─────────────────────────────────────────────
group = property("group")!!
version = property("version")!!

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(property("javaVersion").toString().toInt()))
}

//  ─────────────────────────────────────────────
//  Repositories for all modules
//  ─────────────────────────────────────────────
allprojects {
    group = property("group")!!
    version = property("version")!!

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://oss.sonatype.org/content/groups/public/")
        maven("https://libraries.minecraft.net")
        maven("https://repo.codemc.io/repository/maven-releases/")
        maven("https://repo.codemc.io/repository/maven-snapshots/")
        maven("https://leycm.github.io/repository/")
    }
}

//  ─────────────────────────────────────────────
//  Load gradle.properties from subprojects
//  ─────────────────────────────────────────────
subprojects.forEach { sub ->
    val subProps = sub.file("gradle.properties")
    if (subProps.exists()) {
        Properties().apply {
            subProps.inputStream().use(::load)
        }.forEach { (k, v) ->
            sub.extra[k.toString()] = v
        }
    }
}

//  ─────────────────────────────────────────────
//  Sync root properties into all subprojects
//  ─────────────────────────────────────────────
val rootProps = Properties().apply {
    val file = rootProject.file("gradle.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

rootProps.forEach { (k, v) ->
    rootProject.extra[k.toString()] = v
    subprojects.forEach { sub -> sub.extra[k.toString()] = v }
}

//  ─────────────────────────────────────────────
//  Default configuration for all subprojects
//  ─────────────────────────────────────────────
subprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")
    apply(plugin = "io.freefair.lombok")

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(property("javaVersion").toString().toInt()))
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    tasks.withType<Javadoc> {
        isFailOnError = false
        options.encoding = "UTF-8"
    }

    /**
     * Custom Jar configuration:
     * Combines all runtime dependencies directly into the artifact.
     */
    tasks.withType<Jar> {
        archiveBaseName.set("${rootProject.name}-${project.name}")

        val deps = configurations.runtimeClasspath.get()
        from({
            deps.map { if (it.isDirectory) it else zipTree(it) }
        })

        exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    /**
     * Inherit dependencies from root’s `implementation`
     */
    dependencies {
        rootProject.configurations.findByName("implementation")?.dependencies?.forEach { dep ->
            implementation(dep)
        }
    }

    /**
     * Maven publishing configuration:
     * Publishes to local `../repository`
     */
    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
                groupId = project.group.toString()
                artifactId = project.name
                version = project.version.toString()

                pom {
                    name.set(project.name)
                    description.set("Automatically published by LEYCM build system.")
                    url.set("https://github.com/leycm")

                    licenses {
                        license {
                            name.set("LECP License")
                            url.set("https://github.com/leycm/leycm/blob/main/LICENSE")
                        }
                    }
                    developers {
                        developer {
                            id.set("leycm")
                            name.set("LeyCM")
                            email.set("leycm@proton.me")
                        }
                    }
                }
            }
        }
        repositories {
            maven {
                name = "leycm-repo"
                val repoDir = rootProject.projectDir.parentFile.resolve("repository")
                url = uri(repoDir)
            }
        }
    }

    tasks.register("pushRepo") {
        doLast {
            println("Running push script in repository directory...")
            val repoDir = rootProject.projectDir.parentFile.resolve("repository")
            val script = repoDir.resolve("publish.sh")

            @Suppress("DEPRECATION")
            exec {
                workingDir = repoDir
                commandLine("sh", script.absolutePath)
            }
        }
    }

    tasks.named("publish") {
        finalizedBy("pushRepo")
    }
}

//  ─────────────────────────────────────────────
//  Custom Build Task: `packets`
//  ─────────────────────────────────────────────
tasks.register("packets") {
    group = "build"
    description = "Builds all platform variants and copies them into /out"

    dependsOn(subprojects.mapNotNull { it.tasks.findByName("build") })

    doLast {
        val outDir = rootProject.file("out").apply { mkdirs() }

        subprojects.filter { it.name != "api" }.forEach { project ->
            @Suppress("DEPRECATION")
            val jar = project.buildDir.resolve("libs/${rootProject.name}-${project.name}-${project.version}.jar")
            if (jar.exists()) {
                jar.copyTo(outDir.resolve(jar.name), overwrite = true)
                println("[c] Copied ${jar.name} → /out/")
            }
        }

        println("[✓] All plugin builds finished and moved to /out/")
    }
}
