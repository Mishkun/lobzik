package xyz.mishkun.lobzik.graph

import com.alex_zaitsev.adg.decode.ApkSmaliDecoderController
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class LobzikExplodeApkToSmaliTask : DefaultTask() {
    @get:Input
    abstract val apiVersion: Property<Int>

    @get:InputFile
    abstract val apkFile: RegularFileProperty

    @get:OutputDirectory
    abstract val smaliDirectory: DirectoryProperty

    @TaskAction
    fun explodeApk() {
        ApkSmaliDecoderController.decode(
            apkFile.get().asFile.absolutePath,
            smaliDirectory.get().asFile.absolutePath,
            apiVersion.get()
        )
    }
}
