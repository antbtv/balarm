import androidx.room.gradle.RoomExtension
import com.android.build.api.dsl.CommonExtension
import com.antbtv.balarm.buildlogic.library
import com.antbtv.balarm.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Room через KSP (ADR-004): схемы экспортируются в `schemas/` (в git) и доступны
 * unit-тестам как assets — для `MigrationTestHelper` под Robolectric.
 * Применять после `balarm.android.library`: плагину `androidx.room` нужен уже подключённый AGP.
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("androidx.room")

        val schemas = layout.projectDirectory.dir("schemas")
        extensions.configure<RoomExtension> {
            schemaDirectory(schemas.asFile.path)
        }
        extensions.getByType(CommonExtension::class.java)
            .sourceSets.getByName("test").assets.directories.add(schemas.asFile.path)

        dependencies {
            add("implementation", libs.library("androidx-room-runtime"))
            add("implementation", libs.library("androidx-sqlite-framework"))
            add("ksp", libs.library("androidx-room-compiler"))
            add("testImplementation", libs.library("androidx-room-testing"))
        }
    }
}
