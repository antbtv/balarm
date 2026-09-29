package com.antbtv.balarm.buildlogic.featureflags

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GenerateFeatureFlagsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val configFile: RegularFileProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val source = configFile.get().asFile
        val entries = try {
            parseFeatureFlags(source.readText())
        } catch (e: FeatureFlagsFormatException) {
            throw IllegalStateException("${source.name}: ${e.message}", e)
        }
        val pkg = packageName.get()
        val dir = outputDir.get().asFile.resolve(pkg.replace('.', '/'))
        outputDir.get().asFile.deleteRecursively()
        dir.mkdirs()
        dir.resolve("Feature.kt").writeText(renderFeatureEnum(pkg, entries))
    }
}
