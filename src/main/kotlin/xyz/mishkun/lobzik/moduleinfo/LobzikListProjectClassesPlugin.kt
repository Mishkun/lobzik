package xyz.mishkun.lobzik.moduleinfo

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import xyz.mishkun.lobzik.graph.LobzikApkDepsExtension

class LobzikListProjectClassesPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create("lobzik", LobzikApkDepsExtension::class.java)
        target.extensions.getByType(AndroidComponentsExtension::class.java).onVariants { variant ->
            target.tasks.register<LobzikListProjectClassesTask>("listProjectDependencyGraph${variant.name.capitalized()}") {
                packagePrefix.set(extension.packagePrefix)
                ignoredClasses.set(extension.ignoredClasses)
                kotlinClasses.from(
                    target.tasks.named<KotlinCompile>("compile${variant.name.capitalized()}Kotlin")
                        .map { it.outputs.files.asFileTree }
                )
                javaClasses.from(
                    target.tasks.named<JavaCompile>("compile${variant.name.capitalized()}JavaWithJavac")
                        .map { it.outputs.files.asFileTree }
                )
                classesDependenciesOutput.set(project.layout.buildDirectory.file("reports/${variant.name}/lobzik/dependency_graph.csv"))
            }
        }
    }
}
