
plugins {
    id("com.gtnewhorizons.gtnhconvention")
    jacoco
    id("org.sonarqube") version "7.3.1.8318"
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
