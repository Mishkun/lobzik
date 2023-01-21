package xyz.mishkun.lobzik.graph

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

interface LobzikApkDepsExtension {
    val packagePrefix: Property<String>
    val ignoredClasses: ListProperty<String>
}
