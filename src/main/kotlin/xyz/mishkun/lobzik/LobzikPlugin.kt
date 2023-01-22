package xyz.mishkun.lobzik

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.register
import xyz.mishkun.lobzik.dependencies.aggregate.LobzikAggregateDependenciesTask
import xyz.mishkun.lobzik.dependencies.perproject.LobzikExtension
import xyz.mishkun.lobzik.dependencies.perproject.LobzikProjectDependencyGraphPlugin

class LobzikPlugin: Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create("lobzik", LobzikExtension::class.java)
        val configuration = target.configurations.create("projectDependencyGraph")
        val nodesConfiguration = target.configurations.create("projectDependencyGraphNodes")
        val task = target.tasks.register<LobzikAggregateDependenciesTask>("aggregateProjectDependencyGraph") {
            inputFiles.from(configuration)
            nodeFiles.from(nodesConfiguration)
            csvEdgesOutputFile.set(target.layout.buildDirectory.file("reports/lobzik/dependency_edges.csv"))
            csvNodesOutputFile.set(target.layout.buildDirectory.file("reports/lobzik/dependency_nodes.csv"))
        }
        target.subprojects {  subproject ->
            target.dependencies {
                configuration(project(subproject.name, "projectDependencyGraph"))
                nodesConfiguration(project(subproject.name, "projectDependencyGraphNodes"))
            }
            subproject.pluginManager.withPlugin(ANDROID_APP_PLUGIN) {
                applySubplugin(target, subproject)
            }
            subproject.pluginManager.withPlugin(ANDROID_LIBRARY_PLUGIN) {
                applySubplugin(target, subproject)
            }
            subproject.pluginManager.withPlugin(KOTLIN_JVM_PLUGIN) {
                applySubplugin(target, subproject)
            }
        }
    }

    private fun applySubplugin(
        target: Project,
        subproject: Project,
    ) {
        subproject.apply<LobzikProjectDependencyGraphPlugin>()
    }
}

private const val ANDROID_APP_PLUGIN = "com.android.application"
private const val ANDROID_LIBRARY_PLUGIN = "com.android.library"
private const val KOTLIN_JVM_PLUGIN = "org.jetbrains.kotlin.jvm"
