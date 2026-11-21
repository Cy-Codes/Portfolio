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
    fun `presentation module contains zero Compose imports`() {
        scopeForRelativeDir("app/presentation/src").files.assertFalse { file ->
            file.hasImport { imp ->
                imp.name.startsWith("androidx.compose.") || imp.name.startsWith("org.jetbrains.compose.")
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
    fun `all data classes across core and server modules declare immutable val properties only`() {
        listOf("core/src", "server/server-public/src", "server/server-admin/src").forEach { dir ->
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
