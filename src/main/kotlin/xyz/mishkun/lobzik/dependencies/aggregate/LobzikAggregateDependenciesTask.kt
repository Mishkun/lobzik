package xyz.mishkun.lobzik.dependencies.aggregate

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class LobzikAggregateDependenciesTask: DefaultTask() {

    @get:InputFiles
    abstract val inputFiles: ConfigurableFileCollection

    @get:InputFiles
    abstract val nodeFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val csvEdgesOutputFile: RegularFileProperty

    @get:OutputFile
    abstract val csvNodesOutputFile: RegularFileProperty

    @TaskAction
    fun aggregate() {
        val aggregatedNodes = nodeFiles.files.flatMap { artifact ->
            csvReader().readAllWithHeader(artifact)
        }
        csvWriter().open(csvNodesOutputFile.asFile.get()) {
            writeRow("Id", "Label", "IsInterface", "Module")
            for (edge in aggregatedNodes) {
                writeRow(edge["Id"], edge["Label"], edge["IsInterface"], edge["Module"])
            }
        }
        val aggregatedEdges = inputFiles.files.flatMap { artifact ->
            csvReader().readAllWithHeader(artifact)
        }
        csvWriter().open(csvEdgesOutputFile.asFile.get()) {
            writeRow("Source", "Target", "Weight")
            for (edge in aggregatedEdges) {
                writeRow(edge["Source"], edge["Target"], edge["Weight"])
            }
        }
    }
}
