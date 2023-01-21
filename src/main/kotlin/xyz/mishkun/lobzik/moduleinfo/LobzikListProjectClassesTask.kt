package xyz.mishkun.lobzik.moduleinfo

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

private const val ASM_VERSION = Opcodes.ASM9

abstract class LobzikListProjectClassesTask : DefaultTask() {
    @get:InputFile
    abstract val jarFile: RegularFileProperty

    @get:OutputFile
    abstract val classesListFile: RegularFileProperty

    @TaskAction
    fun explodeJar() {
        val zip = ZipFile(jarFile.get().asFile)
        val classNames = zip.readClassNames()
        classesListFile.get().asFile.writeText(classNames.joinToString(separator = "\n"))
    }

    private fun ZipFile.readClassNames(): Set<String> {
        val visitor = object : ClassVisitor(ASM_VERSION) {
            val classes = mutableSetOf<String>()
            override fun visit(
                version: Int,
                access: Int,
                name: String,
                signature: String?,
                superName: String?,
                interfaces: Array<out String>?,
            ) {
                classes.add(canonicalize(name))
            }
        }
        asSequenceOfClassFiles().forEach { classEntry ->
            val reader = getInputStream(classEntry).use {
                ClassReader(it.readBytes())
            }
            reader.accept(visitor, 0)
        }
        return visitor.classes
    }

    fun canonicalize(className: String): String = className.replace('/', '.')


    private fun ZipFile.asSequenceOfClassFiles(): Sequence<ZipEntry> {
        return entries().asSequence().filter {
            it.name.endsWith(".class") && it.name != "module-info.class"
        }
    }

}
