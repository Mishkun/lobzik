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

dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation("com.android.tools.build:gradle-api:7.3.1")
    implementation(project(":apk-dependency-graph-builder"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
