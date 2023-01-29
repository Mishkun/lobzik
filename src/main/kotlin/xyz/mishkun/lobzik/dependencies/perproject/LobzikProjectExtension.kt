package xyz.mishkun.lobzik.dependencies.perproject

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

interface LobzikProjectExtension {
    val packagePrefix: Property<String>
    val ignoredClasses: ListProperty<String>
    val variantName: Property<String>
}
