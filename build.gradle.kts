plugins {
    java
    application
    `maven-publish`
    id("com.gradleup.shadow") version "9.0.0"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.sigpipe:jbsdiff:1.0")

    constraints {
        implementation("org.apache.commons:commons-compress:1.26.2") {
            because("Mitigates CVEs in older transitive versions: CVE-2024-25710, CVE-2021-35517, CVE-2021-36090")
        }
    }
}

// Compile-Optionen
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

// Reproducible archives across machines/builds
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val appMainClass = "io.epicservices.minecraft.epicclip.Epicclip"

application {
    mainClass.set(appMainClass)
}

// Standard-JAR deaktivieren, Shadow-JAR wird das Hauptartefakt
tasks.jar {
    enabled = false
}

// Shaded/Relocated Jar erzeugen und als Hauptartefakt verwenden
val shadowJarProvider = tasks.named("shadowJar")

tasks.shadowJar {
    archiveClassifier.set("")

    val prefix = "epicclip.libs"
    listOf("org.apache", "org.tukaani", "io.sigpipe").forEach { pack ->
        relocate(pack, "$prefix.$pack")
    }

    manifest {
        attributes("Main-Class" to appMainClass)
    }

    // Lizenz-Datei in META-INF aufnehmen
    from(file("license.txt")) {
        into("META-INF/license")
        rename { "epicclip-LICENSE.txt" }
    }

    // einige META-INF aus Dependencies ausschließen
    exclude("META-INF/LICENSE.txt")
    exclude("META-INF/NOTICE.txt")
}

// Application/Distribution-Tasks explizit vom Shadow-JAR abhängig machen
tasks.named<org.gradle.jvm.application.tasks.CreateStartScripts>("startScripts") {
    dependsOn(shadowJarProvider)
}
tasks.named<org.gradle.api.tasks.bundling.Zip>("distZip") {
    dependsOn(shadowJarProvider)
}
tasks.named<org.gradle.api.tasks.bundling.Tar>("distTar") {
    dependsOn(shadowJarProvider)
}

val isSnapshot = project.version.toString().endsWith("-SNAPSHOT")

publishing {
    publications {
        register<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            // Veröffentliche das Shadow-JAR als Hauptartefakt
            artifact(shadowJarProvider)
            // und die Quellen
            artifact(tasks.named("sourcesJar"))
            withoutBuildIdentifier()

            pom {
                val repoPath = "Epic-Services/Epicclip"
                val repoUrl = "https://github.com/$repoPath"

                name.set("Epicclip")
                description.set(project.description)
                url.set(repoUrl)
                packaging = "jar"

                licenses {
                    license {
                        name.set("MIT")
                        url.set("$repoUrl/blob/main/license.txt")
                        distribution.set("repo")
                    }
                }

                issueManagement {
                    system.set("GitHub")
                    url.set("$repoUrl/issues")
                }

                developers {
                    developer {
                        id.set("30TageBan")
                        name.set("Chris Gewald")
                        email.set("chrisgewald@gmail.com")
                        url.set("https://github.com/30TageBan")
                    }
                }

                scm {
                    url.set(repoUrl)
                    connection.set("scm:git:$repoUrl.git")
                    developerConnection.set("scm:git:git@github.com:$repoPath.git")
                }
            }
        }

        repositories {
            val url = if (isSnapshot) {
                "https://repo.epic-services.io/repository/maven-snapshots/"
            } else {
                "https://repo.epic-services.io/repository/maven-releases/"
            }

            maven(url) {
                credentials(PasswordCredentials::class)
                name = "epic"
            }
        }
    }
}

tasks.register("printVersion") {
    doFirst {
        println(version)
    }
}
