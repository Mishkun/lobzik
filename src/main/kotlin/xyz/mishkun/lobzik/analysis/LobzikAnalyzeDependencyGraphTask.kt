package xyz.mishkun.lobzik.analysis

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import xyz.mishkun.lobzik.graph.GraphRoutine

abstract class LobzikAnalyzeDependencyGraphTask : DefaultTask() {

    @get:Input
    abstract val monolithModule: Property<String>

    @get:Input
    abstract val featureModulesRegex: ListProperty<String>

    @get:InputFile
    abstract val nodesFile: RegularFileProperty

    @get:InputFile
    abstract val edgesFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun analyzeDependencies() {
        outputDir.asFile.get().mkdirs()
        GraphRoutine(
            monolithModule.get(),
            featureModulesRegex.get(),
            nodesFile.asFile.get(),
            edgesFile.asFile.get(),
            outputDir.asFile.get()
        ).doAnalysis()
    }
}
