dependencies {
    compileOnly("io.netty:netty-all:4.1.118.Final")
    compileOnly("com.github.retrooper:packetevents-velocity:2.8.0")
    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    compileOnly("net.kyori:adventure-api:4.17.0")

    implementation(project(":Freesia-Common"))
    implementation("com.electronwill.night-config:toml:3.6.6")
    implementation("org.geysermc.mcprotocollib:protocol:1.21-SNAPSHOT")
    implementation("ca.spottedleaf:concurrentutil:0.0.3")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
}

val targetJavaVersion = 21

java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}

val templateSource = file("src/main/templates")
val templateDest = layout.buildDirectory.dir("generated/sources/templates")

val generateTemplates = tasks.register<Copy>("generateTemplates") {
    val props = mapOf("version" to rootProject.version)
    inputs.properties(props)
    from(templateSource)
    into(templateDest)
    expand(props)
}

sourceSets.named("main") {
    java.srcDir(generateTemplates.map { it.outputs })
}

// 确保 generateTemplates 任务在构建时执行
tasks.named("build") {
    dependsOn(generateTemplates)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.named("shadowJar"))
}
