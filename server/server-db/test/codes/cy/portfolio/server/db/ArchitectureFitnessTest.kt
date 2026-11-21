package codes.cy.portfolio.server.db

import com.lemonappdev.konsist.api.KoModifier
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test

class ArchitectureFitnessTest {
    private val repoRoot: Path = resolveRepoRoot()

    private fun scopeForRelativeDir(relativeDir: String) =
        Konsist.scopeFromExternalDirectory(repoRoot.resolve(relativeDir).toString())

    @Test
    fun `presentation module contains zero Compose, server, or Exposed imports across all KMP source sets`() {
        val presentationDirs = listOf(
            "app/presentation/src",
            "app/presentation/src@jvm",
            "app/presentation/src@android",
            "app/presentation/src@ios",
            "app/presentation/src@wasmJs",
        ).filter { Files.isDirectory(repoRoot.resolve(it)) }

        presentationDirs.forEach { dir ->
            scopeForRelativeDir(dir).files.assertFalse { file ->
                file.hasImport { imp ->
                    imp.name.startsWith("androidx.compose.") ||
                        imp.name.startsWith("org.jetbrains.compose.") ||
                        imp.name.startsWith("io.ktor.server.") ||
                        imp.name.startsWith("org.jetbrains.exposed.")
                }
            }
        }
    }

    @Test
    fun `composeUi module isolates Ktor server Exposed and admin packages across all KMP source sets`() {
        val composeUiDirs = listOf(
            "app/composeUi/src",
            "app/composeUi/src@jvm",
            "app/composeUi/src@android",
            "app/composeUi/src@ios",
            "app/composeUi/src@wasmJs",
        ).filter { Files.isDirectory(repoRoot.resolve(it)) }

        composeUiDirs.forEach { dir ->
            scopeForRelativeDir(dir).files.assertFalse { file ->
                file.hasImport { imp ->
                    imp.name.startsWith("io.ktor.server.") ||
                        imp.name.startsWith("org.jetbrains.exposed.") ||
                        imp.name.startsWith("codes.cy.portfolio.server.admin.")
                }
            }
        }
    }

    @Test
    fun `core module contains zero server, DB, or Compose imports`() {
        scopeForRelativeDir("core/src").files.assertFalse { file ->
            file.hasImport { imp ->
                imp.name.startsWith("io.ktor.server.") ||
                    imp.name.startsWith("org.jetbrains.exposed.") ||
                    imp.name.startsWith("androidx.compose.") ||
                    imp.name.startsWith("org.jetbrains.compose.")
            }
        }
    }

    @Test
    fun `server-db module contains zero Ktor server imports`() {
        scopeForRelativeDir("server/server-db/src").files.assertFalse { file ->
            file.hasImport { imp -> imp.name.startsWith("io.ktor.server.") }
        }
    }

    @Test
    fun `server-public module isolates admin and Exposed symbols`() {
        scopeForRelativeDir("server/server-public/src").files.assertFalse { file ->
            file.hasImport { imp ->
                imp.name.startsWith("codes.cy.portfolio.server.admin.") ||
                    imp.name.startsWith("org.jetbrains.exposed.")
            }
        }
    }

    @Test
    fun `server-admin module isolates Exposed symbols`() {
        scopeForRelativeDir("server/server-admin/src").files.assertFalse { file ->
            file.hasImport { imp -> imp.name.startsWith("org.jetbrains.exposed.") }
        }
    }

    @Test
    fun `adminShared module contains zero Ktor server or Exposed imports across all KMP source sets`() {
        val adminSharedDirs = listOf(
            "app/adminShared/src",
            "app/adminShared/src@jvm",
            "app/adminShared/src@wasmJs",
        ).filter { Files.isDirectory(repoRoot.resolve(it)) }

        adminSharedDirs.forEach { dir ->
            scopeForRelativeDir(dir).files.assertFalse { file ->
                file.hasImport { imp ->
                    imp.name.startsWith("io.ktor.server.") ||
                        imp.name.startsWith("org.jetbrains.exposed.")
                }
            }
        }
    }

    @Test
    fun `public client modules contain zero admin package imports`() {
        val publicClientDirs = listOf(
            "app/presentation/src",
            "app/presentation/src@jvm",
            "app/presentation/src@android",
            "app/presentation/src@ios",
            "app/presentation/src@wasmJs",
            "app/composeUi/src",
            "app/composeUi/src@jvm",
            "app/composeUi/src@android",
            "app/composeUi/src@ios",
            "app/composeUi/src@wasmJs",
            "app/webApp/src",
            "app/desktopApp/src",
            "app/androidApp/src",
            "app/iosApp/src",
        ).filter { Files.isDirectory(repoRoot.resolve(it)) }

        publicClientDirs.forEach { dir ->
            scopeForRelativeDir(dir).files.assertFalse { file ->
                file.hasImport { imp -> imp.name.startsWith("codes.cy.portfolio.admin.") }
            }
        }
    }

    @Test
    fun `all data classes across core, presentation, composeUi, adminShared, and server modules declare immutable val properties only`() {
        listOf(
            "core/src",
            "app/presentation/src",
            "app/composeUi/src",
            "app/adminShared/src",
            "server/server-public/src",
            "server/server-admin/src",
        ).forEach { dir ->
            scopeForRelativeDir(dir)
                .classes()
                .filter { it.hasModifier(KoModifier.DATA) }
                .flatMap { it.properties(includeNested = true) }
                .assertTrue { prop -> !prop.hasVarModifier }
        }
    }

    companion object {
        private fun resolveRepoRoot(): Path =
            generateSequence(Paths.get("").toAbsolutePath().normalize()) { it.parent }
                .firstOrNull { Files.exists(it.resolve("project.yaml")) }
                ?: error("Could not locate repository root containing project.yaml.")
    }
}
