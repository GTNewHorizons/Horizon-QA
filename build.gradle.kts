
import org.gradle.api.publish.maven.MavenPublication

plugins {
    id("com.gtnewhorizons.gtnhconvention")
    jacoco
    id("org.sonarqube") version "7.3.1.8318"
}

val hqaTools = tasks.register<Zip>("hqaTools") {
    group = "build"
    description = "Package the portable client supervisor and report verifier"
    archiveBaseName.set("horizonqa")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("tools")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from("scripts") {
        include("client-tests/build.gradle", "client-tests/settings.gradle", "client-test-isolation.init.gradle")
        into("scripts")
    }
    from("tools") {
        include("verify-client-reports.py", "README.md")
        into("tools")
    }
    from("LICENSE")
}

tasks.named("assemble") { dependsOn(hqaTools) }
plugins.withId("maven-publish") {
    extensions.configure<PublishingExtension> {
        publications.withType<MavenPublication>().configureEach {
            artifact(hqaTools) { classifier = "tools" }
        }
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
    // Resolve dependencies from bytecode even when their API JARs also contain Java sources
    javadocOptions.addStringOption("sourcepath", sourceSets.main.get().allJava.srcDirs.joinToString(File.pathSeparator) { it.absolutePath })

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
