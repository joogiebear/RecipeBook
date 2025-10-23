plugins {
    kotlin("jvm") version "2.1.0"
    id("io.github.goooler.shadow") version "8.1.8"
}

group = "ru.oftendev"
version = findProperty("version")!!

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.auxilor.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.dmulloy2.net/repository/public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.willfp:eco:6.76.2")
    testImplementation(kotlin("test"))
}

tasks {
    compileJava {
        options.isDeprecation = true
        options.encoding = "UTF-8"

        dependsOn(clean)
    }

    processResources {
        filesMatching(listOf("**plugin.yml", "**eco.yml")) {
            expand(
                "version" to project.version,
                "pluginName" to rootProject.name
            )
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}