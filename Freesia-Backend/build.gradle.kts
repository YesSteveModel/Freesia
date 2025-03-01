dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("io.netty:netty-all:4.1.118.Final")
}

tasks.build {
    dependsOn(tasks.named("shadowJar"))
}
