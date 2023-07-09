import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.7.21"
    id("com.gradle.plugin-publish") version "1.2.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "xyz.mishkun.lobzik"
version = "0.7.0"

repositories {
    mavenCentral()
    google()
}

gradlePlugin {
    website.set("https://github.com/Mishkun/lobzik")
    vcsUrl.set("https://github.com/Mishkun/lobzik")
    plugins.create("lobzik-project-level") {
        id = "xyz.mishkun.lobzik-project"
        implementationClass = "xyz.mishkun.lobzik.dependencies.perproject.LobzikProjectDependencyGraphPlugin"
        displayName = "Lobzik project level plugin"
        description = "Gradle plugin to extract project classes dependency graph for using in Lobzik analysis pipeline"
        tags.set(listOf("lobzik", "modularisation", "dependency-analysis"))
    }
    plugins.create("lobzik-top-level") {
        id = "xyz.mishkun.lobzik"
        implementationClass = "xyz.mishkun.lobzik.LobzikPlugin"
        displayName = "Lozbik plugin"
        description = "Lobzik is a gradle plugin designed to help Android developers chop their monolithic codebases into smaller pieces. It gathers data about dependency graph and runs Louvain algorithm to split it into modules"
        tags.set(listOf("lobzik", "modularisation", "dependency-analysis"))
    }
}

kotlin {
    this.jvmToolchain(11)
}


val VERSION_ASM = "9.4"

val shade by configurations.creating
configurations.implementation.configure { extendsFrom(shade) }

dependencies {
    compileOnly(gradleApi())
    compileOnly(gradleKotlinDsl())
    shade(files("libs/gephi-toolkit-0.10.0-all.jar"))
    shadow("org.jetbrains.kotlinx:kotlinx-html-jvm:0.8.0")
    shadow("space.kscience:plotlykt-core:0.5.0")
    shadow("org.jetbrains.kotlin:kotlin-gradle-plugin-api:1.6.10")
    shadow("com.github.doyaaaaaken:kotlin-csv-jvm:1.7.0")
    shadow("com.android.tools.build:gradle-api:7.3.1")
    shadow("org.ow2.asm:asm:$VERSION_ASM")
    shadow("org.ow2.asm:asm-tree:$VERSION_ASM")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<ShadowJar> {
    configurations = listOf(shade)
    archiveClassifier.set("")
    this.isZip64 = true
}
