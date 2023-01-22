package xyz.mishkun.lobzik.dependencies.aggregate

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class LobzikAggregateDependenciesTask: DefaultTask() {

    @get:InputFiles
    abstract val inputFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val csvEdgesOutputFile: RegularFileProperty

    @get:OutputFile
    abstract val csvNodesOutputFile: RegularFileProperty

    @TaskAction
    fun aggregate() {
        val aggregated = inputFiles.files.flatMap { artifact ->
            csvReader().readAllWithHeader(artifact).map { parsed ->
                artifact.name.substringBefore("_dependency_graph") to parsed
            }
        }
        csvWriter().open(csvNodesOutputFile.asFile.get()) {
            writeRow("Id", "Label", "Module")
            aggregated.mapTo(HashSet()) { (module, entry) ->
                module to entry["Source"]
            }.forEach { (module, entry) ->
                writeRow(entry, entry?.substringAfterLast("."), module)
            }
        }
        csvWriter().open(csvEdgesOutputFile.asFile.get()) {
            writeRow("Source", "Target", "Weight")
            aggregated.forEach { (_, entry) ->
                writeRow(entry["Source"], entry["Target"], entry["Weight"])
            }
        }
    }
}
