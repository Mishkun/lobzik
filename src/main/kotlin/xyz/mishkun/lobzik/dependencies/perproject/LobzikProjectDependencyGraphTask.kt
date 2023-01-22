package xyz.mishkun.lobzik.dependencies.perproject

import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import java.io.File

abstract class LobzikProjectDependencyGraphTask : DefaultTask() {

    private val logger = getLogger()

    @get:Input
    abstract val packagePrefix: Property<String>

    @get:Input
    abstract val ignoredClasses: ListProperty<String>

    @get:Classpath
    @get:InputFiles
    abstract val kotlinClasses: ConfigurableFileCollection

    @get:Classpath
    @get:InputFiles
    abstract val javaClasses: ConfigurableFileCollection

    @get:OutputFile
    abstract val classesDependenciesOutput: RegularFileProperty

    @TaskAction
    fun analyzeDependencies() {
        val files = javaClasses.asFileTree
            .plus(kotlinClasses.asFileTree)
            .filterToClassFiles()
            .files

        val parsedDeps = parseClassDependencies(files)
            .flattenClasses()

        csvWriter().open(classesDependenciesOutput.get().asFile) {
            writeRow("Source", "Target", "Weight")
            for (dep in parsedDeps) {
                writeRow(dep.name, dep.dependsOn, dep.times)
            }
        }
    }

    private fun List<ClassDependency>.flattenClasses(): List<ClassDependency> {
        return groupBy { it.name to it.dependsOn }
            .map { (key, value) -> ClassDependency(key.first, key.second, value.sumOf { it.times }) }
    }

    private fun parseClassDependencies(files: Set<File>): List<ClassDependency> {
        val ignoredClassesFilter = ignoredClasses.get().map { it.toRegex() }
        return files.flatMap { classFile ->
            val analyzer = classFile.inputStream().use {
                val analyzer = ClassAnalyzer(logger)
                ClassReader(it).accept(analyzer, 0)
                analyzer
            }
            val canonicalSource = canonicalize(analyzer.className)
            if (canonicalSource.isOk(packagePrefix.get(), ignoredClassesFilter)) {
                analyzer.classes.mapNotNull { (dependendant, times) ->
                    val canonicalTarget = canonicalize(dependendant)
                    ClassDependency(
                        canonicalSource,
                        canonicalTarget,
                        times
                    ).takeIf { canonicalTarget.isOk(packagePrefix.get(), ignoredClassesFilter) }
                }
            } else {
                emptyList()
            }
        }
    }

    private fun String.isOk(prefix: String, filter: List<Regex>) =
        startsWith(prefix) && filter.none { it.matches(getClassSimpleName(this)) }

    private fun getClassSimpleName(fullClassName: String): String =
        fullClassName.substringBefore('<').substringAfterLast('.')

    private fun canonicalize(className: String): String = className.replace('/', '.')
        .substringBefore('$')

    private fun FileCollection.filterToClassFiles(): FileCollection {
        return filter {
            it.isFile && it.name.endsWith(".class") && it.name != "module-info.class"
        }
    }

    private data class ClassDependency(val name: String, val dependsOn: String, val times: Int)
}
