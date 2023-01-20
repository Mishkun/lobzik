plugins {
    kotlin("jvm") version "1.7.21"
}

group = "xyz.mishkun.lobzik"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
