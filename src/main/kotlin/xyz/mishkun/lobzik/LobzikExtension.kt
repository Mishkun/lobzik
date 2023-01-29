package xyz.mishkun.lobzik

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import xyz.mishkun.lobzik.dependencies.perproject.LobzikProjectExtension

interface LobzikExtension : LobzikProjectExtension {
    val featureModulesRegex: ListProperty<String>
    val monolithModule: Property<String>
}
