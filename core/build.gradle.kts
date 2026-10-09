group = "com.willfp"
version = rootProject.version

subprojects {
    dependencies {
        compileOnly("com.willfp:eco:${findProperty("eco-version")}")
    }
}

dependencies {
    implementation(project(":core:common"))
}

tasks {
    shadowJar {
        relocate("dev.romainguy.kotlin.math", "com.willfp.libreforge.libs.math")
        relocate("org.apache.maven", "com.willfp.eco.libs.maven")
        relocate("com.willfp.modelenginebridge", "com.willfp.libreforge.libs.modelenginebridge")
    }

    build {
        dependsOn("buildStandalone")
    }

    val buildStandalone by registering(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
        group = "build"
        description = "Builds a standalone libreforge jar with Kotlin relocated, placed in bin/"

        from(project(":core:common").sourceSets["main"].output)
        from(sourceSets["main"].output)

        configurations = listOf(
            project.configurations.runtimeClasspath.get(),
            project(":core:common").configurations.runtimeClasspath.get()
        )

        archiveBaseName.set("libreforge-standalone")
        archiveClassifier.set("")
        archiveVersion.set(rootProject.version.toString())
        destinationDirectory.set(layout.buildDirectory)

        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        relocate("dev.romainguy.kotlin.math", "com.willfp.libreforge.libs.math")
        relocate("org.apache.maven", "com.willfp.eco.libs.maven")
        relocate("com.willfp.modelenginebridge", "com.willfp.libreforge.libs.modelenginebridge")
        relocate("kotlin", "com.willfp.eco.libs.kotlin")
        relocate("org.jetbrains.kotlin", "com.willfp.eco.libs.kotlin.jetbrains")
        relocate("org.intellij.lang.annotations", "com.willfp.eco.libs.intellij.annotations")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            artifactId = "libreforge"
            groupId = "com.willfp"

            artifact(tasks.shadowJar.get().archiveFile.get()) {
                classifier = "shadow"
            }
        }
    }
}
