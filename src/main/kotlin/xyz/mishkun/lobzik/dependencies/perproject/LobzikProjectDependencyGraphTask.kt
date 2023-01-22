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
    abstract val projectName: Property<String>

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

    @get:OutputFile
    abstract val classesNodeInfoOutput: RegularFileProperty

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
            for (dep in parsedDeps.flatMap { info -> info.dependencies.map { ClassDependency(info.name, it.key, it.value) } }) {
                writeRow(dep.name, dep.dependsOn, dep.times)
            }
        }
        csvWriter().open(classesNodeInfoOutput.get().asFile) {
            writeRow("Id", "Label", "IsInterface", "Module")
            for (info in parsedDeps) {
                writeRow(info.name, getClassSimpleName(info.name), info.isInterface, projectName.get())
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun List<ClassInfo>.flattenClasses(): List<ClassInfo> {
        return groupBy { it.name }
            .map { (key, value) ->
                ClassInfo(key, value.first().isInterface, dependencies = value.fold(mapOf()) { acc, item ->
                    buildMap {
                        putAll(acc)
                        for (entry in item.dependencies.entries) {
                            merge(entry.key, entry.value, Int::plus)
                        }
                    }
                })
            }
    }

    private fun parseClassDependencies(files: Set<File>): List<ClassInfo> {
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
                    ClassInfo(
                        canonicalSource,
                        analyzer.isInterface,
                        mapOf(canonicalTarget to times)
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

    private data class ClassInfo(val name: String, val isInterface: Boolean, val dependencies: Map<String, Int>)

    private data class ClassDependency(val name: String, val dependsOn: String, val times: Int)
}
