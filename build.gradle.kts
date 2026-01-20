plugins {
    id("java")
}

group = findProperty("pluginGroup") as String? ?: "com.error"
version = findProperty("pluginVersion") as String? ?: "0.0.0"
description = findProperty("pluginDescription") as String? ?: "Error"

repositories {
    mavenLocal()
    mavenCentral()
}

var userHome = System.getProperty("user.home")
var hytaleHome = when (System.getProperty("os.name").lowercase()) {
    "windows" -> userHome + "/AppData/Roaming/Hytale"
    "linux" -> userHome + "/.var/app/com.hypixel.HytaleLauncher/data/Hytale"
    else -> userHome + "/Hytale"
}
var hytaleJar = "$hytaleHome/install/release/package/game/latest/Server/HytaleServer.jar"

dependencies {
    // Hytale Server API
    implementation(files(hytaleJar))
}

tasks {
    // Configure Java compilation
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release = 25
    }
    
    // Configure resource processing
    processResources {
        filteringCharset = Charsets.UTF_8.name()
        
        // Replace placeholders in manifest.json
        val props = mapOf(
            "group" to project.group,
            "version" to project.version,
            "description" to project.description
        )
        inputs.properties(props)
        
        filesMatching("manifest.json") {
            expand(props)
        }
    }
}

// Configure Java toolchain
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
