import java.util.Properties
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.21"
    id("fabric-loom") version "1.10.5"
}

val props = Properties().apply {
    rootDir.resolve("gradle.properties").bufferedReader().use { load(it) }
}

val minecraft: String by props
val fabricLoader: String by props
val yarnMappings: String by props
val fabricApi: String by props
val fabricKotlin: String by props
val modId: String by props
val modVersion: String by props
val modGroup = props.getProperty("group")

group = modGroup
version = modVersion

base {
    archivesName.set(modId)
}

repositories {
    maven("https://maven.fabricmc.net") {
        name = "Fabric"
    }
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")

    modImplementation("net.fabricmc:fabric-loader:$fabricLoader")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApi")
    modImplementation("net.fabricmc:fabric-language-kotlin:$fabricKotlin")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", version)
    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "version" to version,
                "modid" to modId
            )
        )
    }
}
