package xyz.mishkun.lobzik.dependencies.perproject

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

interface LobzikProjectDependencyGraphExtension {
    val packagePrefix: Property<String>
    val ignoredClasses: ListProperty<String>
}
