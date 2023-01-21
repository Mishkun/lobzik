plugins {
    kotlin("jvm") version "1.7.21"
    `java-gradle-plugin`
}

group = "xyz.mishkun.lobzik"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
    google()
}

gradlePlugin {
    plugins.create("lobzik-apk-dependency-analyzer") {
        id = "xyz.mishkun.lobzik.apkdeps"
        implementationClass = "xyz.mishkun.lobzik.graph.LobzikApkDependenciesBuilderPlugin"
    }
}

gradlePlugin {
    plugins.create("lobzik-project-dependency-analyzer") {
        id = "xyz.mishkun.lobzik.projectdeps"
        implementationClass = "xyz.mishkun.lobzik.moduleinfo.LobzikListProjectClassesPlugin"
    }
}


val VERSION_ASM = "9.4"
dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.10")
    implementation("com.github.doyaaaaaken:kotlin-csv-jvm:1.7.0")
    implementation("com.android.tools.build:gradle-api:7.3.1")
    implementation(project(":apk-dependency-graph-builder"))
    implementation("org.ow2.asm:asm:$VERSION_ASM")
    implementation("org.ow2.asm:asm-tree:$VERSION_ASM")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
