plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
    maven(url = "https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
    maven(url = "https://raw.github.com/gephi/gephi/mvn-thirdparty-repo/")
}

kotlin {
    this.jvmToolchain(11)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.8.0")
    implementation("space.kscience:plotlykt-core:0.5.0")
    implementation(files("libs/gephi-toolkit-0.10.0-all.jar"))
}
