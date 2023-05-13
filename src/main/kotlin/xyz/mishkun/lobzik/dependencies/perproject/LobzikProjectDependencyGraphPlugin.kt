package xyz.mishkun.lobzik.dependencies.perproject

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
import xyz.mishkun.lobzik.LobzikExtension

class LobzikProjectDependencyGraphPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val rootExtension = target.rootProject.extensions.getByType<LobzikProjectExtension>()
        val extension = target.extensions.create("lobzik", LobzikProjectExtension::class.java)
        extension.variantName.convention(rootExtension.variantName)
        extension.packagePrefix.convention(rootExtension.packagePrefix)
        extension.ignoredClasses.convention(rootExtension.ignoredClasses)
        val androidComponentsExtension = target.extensions.findByType(AndroidComponentsExtension::class.java)
        val graphConfiguration = target.configurations.create("projectDependencyGraph")
        val nodesConfiguration = target.configurations.create("projectDependencyGraphNodes")
        if (androidComponentsExtension != null) {
            androidComponentsExtension.onVariants(
                androidComponentsExtension.selector().withName(extension.variantName.get().toPattern())
            ) { variant ->
                val task = target.tasks.register<LobzikProjectDependencyGraphTask>(
                    "listProjectDependencyGraph${variant.name.capitalized()}"
                ) {
                    packagePrefix.set(extension.packagePrefix)
                    projectName.set(target.path)
                    ignoredClasses.set(extension.ignoredClasses)
                    kotlinClasses.from(
                        target.tasks.named("compile${variant.name.capitalized()}Kotlin", KotlinCompile::class.java)
                    )
                    javaClasses.from(
                        target.tasks.named<JavaCompile>("compile${variant.name.capitalized()}JavaWithJavac")
                    )
                    classesDependenciesOutput.set(project.layout.buildDirectory.file("reports/${variant.name}/lobzik/edges.csv"))
                    classesNodeInfoOutput.set(project.layout.buildDirectory.file("reports/${variant.name}/lobzik/nodes.csv"))
                }
                target.artifacts {
                    it.add(graphConfiguration.name, task.flatMap { it.classesDependenciesOutput })
                    it.add(nodesConfiguration.name, task.flatMap { it.classesNodeInfoOutput })
                }
            }
        } else {
            val task = target.tasks.register<LobzikProjectDependencyGraphTask>("listProjectDependencyGraph") {
                packagePrefix.set(extension.packagePrefix)
                ignoredClasses.set(extension.ignoredClasses)
                projectName.set(target.path)
                kotlinClasses.from(
                    target.tasks.named("compileKotlin", KotlinCompile::class.java)
                )
                javaClasses.from(
                    target.tasks.named<JavaCompile>("compileJava")
                )
                classesDependenciesOutput.set(project.layout.buildDirectory.file("reports/lobzik/edges.csv"))
                classesNodeInfoOutput.set(project.layout.buildDirectory.file("reports/lobzik/nodes.csv"))
            }
            target.artifacts {
                it.add(graphConfiguration.name, task.flatMap { it.classesDependenciesOutput })
                it.add(nodesConfiguration.name, task.flatMap { it.classesNodeInfoOutput })
            }
        }
    }
}
