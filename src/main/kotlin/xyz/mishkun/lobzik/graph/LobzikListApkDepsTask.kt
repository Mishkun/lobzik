package xyz.mishkun.lobzik.graph

import com.alex_zaitsev.adg.FilterProvider
import com.alex_zaitsev.adg.SmaliAnalyzer
import com.alex_zaitsev.adg.io.Filters
import com.alex_zaitsev.adg.io.Writer
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

abstract class LobzikListApkDepsTask : DefaultTask() {
    @get:Input
    abstract val packagePrefix: Property<String>

    @get:Input
    abstract val ignoredClasses: ListProperty<String>

    @get:InputDirectory
    abstract val smaliDirectory: DirectoryProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun extractDependencyGraphFromTheExplodedApk() {
        val filters = Filters(
            packagePrefix.get(),
            false,
            ignoredClasses.get().toTypedArray()
        )
        val filterProvider = FilterProvider(filters)
        val pathFilter = filterProvider.makePathFilter()
        val classFilter = filterProvider.makeClassFilter()
        val analyzer = SmaliAnalyzer(
            smaliDirectory.get().asFile.absolutePath,
            filters,
            pathFilter,
            classFilter
        )

        if (analyzer.run()) {
            Writer(reportFile.asFile.get()).write(analyzer.dependencies)
        }

    }
}
