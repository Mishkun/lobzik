package xyz.mishkun.lobzik.graph

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.kotlin.dsl.register

class LobzikApkDependenciesBuilderPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create("lobzik", LobzikApkDepsExtension::class.java)
        target.extensions.getByType(ApplicationAndroidComponentsExtension::class.java).onVariants { variant ->
            val smaliDir = target.layout.buildDirectory.dir("intermediates/smali/${variant.name}")
            val explodeTask = target.tasks.register<LobzikExplodeApkToSmaliTask>("explodeApk${variant.name.capitalized()}") {
                apiVersion.set(variant.targetSdkVersion.apiLevel)
                apkFile.set(
                    variant.artifacts.get(SingleArtifact.APK).flatMap {
                        project.layout.buildDirectory.file(
                            variant.artifacts.getBuiltArtifactsLoader().load(it)!!.elements.first().outputFile
                        )
                    })
                smaliDirectory.set(smaliDir)
            }
            val buildApkGraphTask =
                target.tasks.register<LobzikListApkDepsTask>("listApkDependencies${variant.name.capitalized()}") {
                    packagePrefix.set(extension.packagePrefix)
                    ignoredClasses.set(extension.ignoredClasses)
                    smaliDirectory.set(explodeTask.flatMap { it.smaliDirectory })
                    reportFile.set(target.layout.buildDirectory.file("reports/lobzik/${variant.name}/apk_dependencies.csv"))
                }
        }
    }
}
