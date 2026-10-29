
import com.gtnewhorizons.gtnhgradle.tasks.RunHotswappableMinecraftTask
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

dependencies {
    compileOnly("com.github.GTNewHorizons:lwjgl3ify:3.0.25:api")
    compileOnly("org.lwjgl:lwjgl-sdl:3.4.2")
}

tasks.register("prepareClientTests") {
    group = "verification"
    description = "Prepare the client runtime without starting Minecraft"
    dependsOn(provider {
        val client = tasks.named("runClient").get()
        client.taskDependencies.getDependencies(client)
    })
}

// An explicit local artifact enables the native-window example without adding Offscreen to ordinary runs
providers.gradleProperty("offscreenJar").orNull?.let { path ->
    val offscreenJar = file(path)
    require(offscreenJar.isFile) { "offscreenJar must name an existing Offscreen dev JAR: $path" }
    dependencies {
        add("runtimeOnlyNonPublishable", files(offscreenJar))
    }
    configurations.configureEach {
        resolutionStrategy.force("com.github.GTNewHorizons:lwjgl3ify:3.0.25")
    }
    tasks.named<RunHotswappableMinecraftTask>("runClient17") {
        targetJvmVersion.set(21)
        javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
        enableHotswap.set(false)
    }
}
