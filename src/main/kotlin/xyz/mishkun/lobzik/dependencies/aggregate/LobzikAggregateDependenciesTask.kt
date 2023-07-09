package xyz.mishkun.lobzik.dependencies.aggregate

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import kotlin.math.log

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
        if (aggregatedNodes.isEmpty()) throw IllegalStateException("No nodes found in project. Did you configure lobzik properly?")
        logger.lifecycle("Module names are: " + aggregatedNodes.map { it["Module"] }.toSet().joinToString())
        logger.lifecycle("Total nodes extracted ${aggregatedNodes.size}")
        csvWriter().open(csvNodesOutputFile.asFile.get()) {
            writeRow("Id", "Label", "IsInterface", "Module")
            for (edge in aggregatedNodes) {
                writeRow(edge["Id"], edge["Label"], edge["IsInterface"], edge["Module"])
            }
        }
        val aggregatedEdges = inputFiles.files.flatMap { artifact ->
            csvReader().readAllWithHeader(artifact)
        }
        if (aggregatedEdges.isEmpty()) throw IllegalStateException("No edges found in project. Did you configure lobzik properly?")
        logger.lifecycle("Total edges extracted ${aggregatedEdges.size}")
        csvWriter().open(csvEdgesOutputFile.asFile.get()) {
            writeRow("Source", "Target", "Weight")
            for (edge in aggregatedEdges) {
                writeRow(edge["Source"], edge["Target"], edge["Weight"])
            }
        }
    }
}
