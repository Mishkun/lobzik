plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
    maven(url = "https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
    maven(url = "https://raw.github.com/gephi/gephi/mvn-thirdparty-repo/")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.8.0")
    implementation(files("libs/gephi-toolkit-0.10.0-all.jar"))
}
