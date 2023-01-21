package xyz.mishkun.lobzik.moduleinfo

import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

private const val ASM_VERSION = Opcodes.ASM9

abstract class LobzikListProjectClassesTask : DefaultTask() {

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
        csvWriter().open(classesDependenciesOutput.get().asFile) {
            writeRow("Source", "Target", "Weight")
            for (dep in parsedDeps) {
                writeRow(dep.name, dep.dependsOn, dep.times)
            }
        }
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

    private fun String.isOk(prefix: String, filter: List<Regex>) = startsWith(prefix) && filter.none { it.matches(this) }

    private fun canonicalize(className: String): String = className.replace('/', '.')

    private fun FileCollection.filterToClassFiles(): FileCollection {
        return filter {
            it.isFile && it.name.endsWith(".class") &&  it.name != "module-info.class"
        }
    }

    private data class ClassDependency(val name: String, val dependsOn: String, val times: Int)

    private fun ZipFile.asSequenceOfClassFiles(): Sequence<ZipEntry> {
        return entries().asSequence().filter {
            it.name.endsWith(".class") && it.name != "module-info.class"
        }
    }

}
