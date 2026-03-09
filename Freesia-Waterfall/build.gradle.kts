repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    compileOnly("io.netty:netty-all:4.1.118.Final")
    compileOnly("com.github.retrooper:packetevents-bungeecord:2.8.0")
    compileOnly("io.github.waterfallmc:waterfall-api:1.20-R0.1-SNAPSHOT")

    implementation(project(":Freesia-Common"))
    implementation("com.electronwill.night-config:toml:3.6.6")
    implementation("org.geysermc.mcprotocollib:protocol:1.21-SNAPSHOT")
    implementation("ca.spottedleaf:concurrentutil:0.0.3")
    implementation("net.kyori:adventure-api:4.14.0")
    implementation("net.kyori:adventure-text-minimessage:4.14.0")
    implementation("net.kyori:adventure-text-serializer-legacy:4.14.0")
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

// Ensure generateTemplates runs during build
tasks.named("build") {
    dependsOn(generateTemplates)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.named("shadowJar"))
}
