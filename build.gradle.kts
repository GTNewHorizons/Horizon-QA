import java.util.UUID
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Zip

plugins {
    id("com.gtnewhorizons.gtnhconvention")
    jacoco
    id("org.sonarqube") version "7.3.1.8318"
}

val toolsVersion = providers.environmentVariable("RELEASE_VERSION").orElse(provider { project.version.toString() })
val toolsVersionFile = layout.buildDirectory.file("generated/hqaTools/hqa_tools/version.txt")
val generateHqaToolsVersion = tasks.register("generateHqaToolsVersion") {
    inputs.property("version", toolsVersion)
    outputs.file(toolsVersionFile)
    doLast {
        toolsVersionFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText("${toolsVersion.get()}\n", Charsets.UTF_8)
        }
    }
}

val hqaTools = tasks.register<Zip>("hqaTools") {
    group = "build"
    description = "Build the standalone, versioned Horizon-QA CI tools archive."
    dependsOn(generateHqaToolsVersion)
    archiveBaseName.set("horizonqa")
    archiveVersion.set(toolsVersion)
    archiveClassifier.set("tools")
    archiveExtension.set("pyz")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from("tools") {
        include("hqa_tools/**")
        exclude("**/__pycache__/**")
    }
    from("tools/hqa_tools/__main__.py")
    from(toolsVersionFile) { into("hqa_tools") }
    from("LICENSE")
}

tasks.named("assemble") { dependsOn(hqaTools) }
plugins.withId("maven-publish") {
    extensions.configure<PublishingExtension> {
        publications.withType<MavenPublication>().configureEach {
            artifact(hqaTools) {
                classifier = "tools"
                extension = "pyz"
            }
        }
    }
}

val hqaToolsTest = tasks.register<Exec>("hqaToolsTest") {
    group = "verification"
    description = "Check the standalone tools and report protocol. Requires Python 3.9+."
    dependsOn(hqaTools)
    workingDir("tools")
    commandLine("python3", "-m", "unittest", "discover", "-s", "tests", "-p", "test_*.py", "-v")
    doFirst { environment("HQA_TOOLS_ARCHIVE", hqaTools.get().archiveFile.get().asFile.absolutePath) }
}
tasks.named("check") { dependsOn(hqaToolsTest) }

tasks.register<Exec>("hqaClientTest") {
    group = "verification"
    description = "Run real client examples with isolated, bounded supervision."
    dependsOn(hqaTools)
    doFirst {
        val reportDir = providers.gradleProperty("hqaReportDir").orNull?.let { file(it) }
            ?: layout.buildDirectory.dir("horizonqa/client-${UUID.randomUUID()}").get().asFile
        commandLine(
            "python3", hqaTools.get().archiveFile.get().asFile.absolutePath, "run-client",
            "--cwd", rootDir.absolutePath,
            "--gradle-task", providers.gradleProperty("hqaClientTask").getOrElse(":examples:runClient"),
            "--tests", providers.gradleProperty("hqaTests").getOrElse("horizonqaexamples:ClientInteractionTests"),
            "--report-dir", reportDir.absolutePath,
            "--timeout", providers.gradleProperty("hqaTimeout").getOrElse("900")
        )
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        csv.required.set(false)
        html.required.set(true)
    }
}

tasks.named("sonar") {
    dependsOn(tasks.jacocoTestReport)
}

sonar {
    properties {
        property("sonar.projectKey", "GTNewHorizons_Horizon-QA")
        property("sonar.organization", "gtnewhorizons")
    }
}

// Configure Javadoc task to prevent GitHub Actions from failing
tasks.withType<Javadoc>().configureEach {
    val javadocOptions = options as StandardJavadocDocletOptions

    // Prevent the build from failing due to missing/incomplete Javadocs
    javadocOptions.addStringOption("Xdoclint:none", "-quiet")

    // Ensure special characters display correctly in MkDocs
    javadocOptions.encoding = "UTF-8"
    javadocOptions.charSet = "UTF-8"

    javadocOptions.windowTitle = "Horizon-QA v${project.version} API Documentation"
    javadocOptions.docTitle = "<h1>Horizon-QA Testing Framework - v${project.version}</h1>"

    // Clean up CI logs
    javadocOptions.quiet()
}

// The java17Dependencies configuration in subprojects requires rfgDeobfuscatorTransformed=true when
// resolving variants. External JARs acquire this attribute through the RFG deobfuscator transform,
// but local project dependencies (devOnlyNonPublishable(project(":"))) bypass that transform.
// Declare the attribute on runtimeElements variants so Gradle can disambiguate them.
afterEvaluate {
    val rfgDeobfAttr = org.gradle.api.attributes.Attribute.of("rfgDeobfuscatorTransformed", Boolean::class.javaObjectType)
    configurations["runtimeElements"].attributes {
        attribute(rfgDeobfAttr, true)
    }
}
