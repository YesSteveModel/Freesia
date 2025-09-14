plugins {
    id("fabric-loom") version "1.10-SNAPSHOT"
    id("maven-publish")
}

configurations {
    create("customShadow")
}

dependencies {
    minecraft("com.mojang:minecraft:${rootProject.extra["minecraft_version"]}")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${rootProject.extra["minecraft_version"]}:${rootProject.extra["parchment_version"]}@zip")
    })

    include(project(":Freesia-Common"))
    implementation(project(":Freesia-Common"))
    implementation("com.electronwill.night-config:core:${rootProject.extra["night_config_version"]}")
    include("com.electronwill.night-config:core:${rootProject.extra["night_config_version"]}")
    include("ca.spottedleaf:concurrentutil:0.0.3")


    implementation("com.electronwill.night-config:toml:${rootProject.extra["night_config_version"]}")
    include("com.electronwill.night-config:toml:${rootProject.extra["night_config_version"]}")

    modImplementation("net.fabricmc:fabric-loader:${rootProject.extra["fabric_loader_version"]}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${rootProject.extra["fabric_version"]}")
}

loom {
    accessWidenerPath.set(file("src/main/resources/freesia_worker.accesswidener"))
}

val processResources by tasks.named<ProcessResources>("processResources") {
    inputs.property("version", rootProject.version)
    inputs.property("minecraft_version", rootProject.extra["minecraft_version"])
    inputs.property("fabric_loader_version", rootProject.extra["fabric_loader_version"])
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to rootProject.version,
            "minecraft_version" to rootProject.extra["minecraft_version"],
            "fabric_loader_version" to rootProject.extra["fabric_loader_version"]
        )
    }
}

val targetJavaVersion = 21

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}

java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
    withSourcesJar()
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.name}" }
    }
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    configurations = listOf(project.configurations.getByName("customShadow"))
    dependsOn(tasks.named("remapJar"))
}
