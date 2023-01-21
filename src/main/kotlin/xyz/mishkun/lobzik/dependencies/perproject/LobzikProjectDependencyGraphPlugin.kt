package xyz.mishkun.lobzik.dependencies.perproject

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile

class LobzikProjectDependencyGraphPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create("lobzik", LobzikProjectDependencyGraphExtension::class.java)
        target.extensions.findByType(AndroidComponentsExtension::class.java)?.onVariants { variant ->
            target.tasks.register<LobzikProjectDependencyGraphTask>("listProjectDependencyGraph${variant.name.capitalized()}") {
                packagePrefix.set(extension.packagePrefix)
                ignoredClasses.set(extension.ignoredClasses)
                kotlinClasses.from(
                    target.tasks.named("compile${variant.name.capitalized()}Kotlin", KotlinCompile::class.java)
                        .map { it.outputs.files.asFileTree }
                )
                javaClasses.from(
                    target.tasks.named<JavaCompile>("compile${variant.name.capitalized()}JavaWithJavac")
                        .map { it.outputs.files.asFileTree }
                )
                classesDependenciesOutput.set(project.layout.buildDirectory.file("reports/${variant.name}/lobzik/dependency_graph.csv"))
            }
        } ?: target.tasks.register<LobzikProjectDependencyGraphTask>("listProjectDependencyGraph") {
            packagePrefix.set(extension.packagePrefix)
            ignoredClasses.set(extension.ignoredClasses)
            kotlinClasses.from(
                target.tasks.named("compileKotlin", KotlinCompile::class.java)
                    .map { it.outputs.files.asFileTree }
            )
            javaClasses.from(
                target.tasks.named<JavaCompile>("compileJava")
                    .map { it.outputs.files.asFileTree }
            )
            classesDependenciesOutput.set(project.layout.buildDirectory.file("reports/lobzik/dependency_graph.csv"))
        }
    }
}
