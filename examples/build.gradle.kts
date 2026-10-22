
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

tasks.register("prepareClientTests") {
    group = "verification"
    description = "Prepare the client runtime without starting Minecraft"
    dependsOn(provider {
        val client = tasks.named("runClient").get()
        client.taskDependencies.getDependencies(client)
    })
}
