plugins {
    kotlin("jvm") version "1.7.21"
    id("com.gradle.plugin-publish") version "1.2.0"
}

group = "xyz.mishkun.lobzik"
version = "0.1.0"

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


val VERSION_ASM = "9.4"

dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation(project(":graph-processing"))
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:1.6.10")
    implementation("com.github.doyaaaaaken:kotlin-csv-jvm:1.7.0")
    implementation("com.android.tools.build:gradle-api:7.3.1")
    implementation("org.ow2.asm:asm:$VERSION_ASM")
    implementation("org.ow2.asm:asm-tree:$VERSION_ASM")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
