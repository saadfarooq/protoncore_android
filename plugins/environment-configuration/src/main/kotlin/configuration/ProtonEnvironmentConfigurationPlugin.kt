/*
 * Copyright (c) 2022 Proton Technologies AG
 * This file is part of Proton AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */
package configuration

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.gradle.BaseExtension
import configuration.extensions.environmentConfiguration
import configuration.extensions.mergeWith
import configuration.extensions.printEnvironmentConfigDetails
import configuration.extensions.sourceClassContent
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

class ProtonEnvironmentConfigurationPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.logger.info("Applying Proton environment configurations for ${target.name}")

        val androidComponents = target.extensions.getByType(AndroidComponentsExtension::class.java)

        androidComponents.onVariants { variant ->
            val appExtension = target.extensions.getByType(BaseExtension::class.java)

            val defaultConfig = appExtension.defaultConfig.environmentConfiguration
            val buildType = appExtension.buildTypes.getByName(variant.buildType!!)
                .environmentConfiguration
            val flavor = variant.flavorName?.let {
                appExtension.productFlavors.getByName(it).environmentConfiguration
            } ?: EnvironmentConfig()

            val mergedConfig = mergeConfigurations(defaultConfig, buildType, flavor)
            target.printEnvironmentConfigDetails(mergedConfig)

            val variantLocation = "${variant.flavorName ?: ""}/${variant.buildType}"
            val envDir = target.layout.buildDirectory.dir(
                "generated/source/envConfig/$variantLocation"
            )

            val taskProvider = target.tasks.register(
                "generate${variant.name}EnvironmentConfig",
                GenerateEnvConfigTask::class.java
            )
            taskProvider.configure {
                sourceContent.set(mergedConfig.sourceClassContent())
                outputDir.set(envDir)
            }

            variant.sources.java?.addGeneratedSourceDirectory(
                taskProvider,
                GenerateEnvConfigTask::outputDir
            )
        }
    }

    private fun mergeConfigurations(vararg configs: EnvironmentConfig): EnvironmentConfig =
        configs.reduce { first, other -> first.mergeWith(other) }

    companion object {
        const val ENV_CONFIG_LOCATION: String = "generated/source/envConfig"
        const val PACKAGE_NAME: String = "me.proton.core.configuration"
        const val DEFAULTS_CLASS_NAME: String = "EnvironmentConfigurationDefaults"
    }
}

abstract class GenerateEnvConfigTask : DefaultTask() {

    @get:Input
    abstract val sourceContent: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val pkgDir = outputDir.get().asFile.resolve("me/proton/core/configuration")
        pkgDir.mkdirs()
        pkgDir.resolve("EnvironmentConfigurationDefaults.java").writeText(sourceContent.get())
    }
}
