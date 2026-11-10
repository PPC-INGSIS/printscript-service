package snippetsearcher.printscript.common

import org.printscript.common.Version

// Una versión inexistente es un error esperado del pedido: se devuelve, no se lanza
sealed interface VersionResolution {
    data class Supported(
        val version: Version,
    ) : VersionResolution

    data class Unsupported(
        val message: String,
    ) : VersionResolution
}

// Las entradas del enum van de la más vieja a la más nueva
fun latestVersion(): Version = Version.entries.last()

fun resolveVersion(id: String): VersionResolution =
    Version.of(id)?.let { VersionResolution.Supported(it) }
        ?: VersionResolution.Unsupported(
            "La versión '$id' no existe. Versiones disponibles: ${Version.entries.joinToString { it.id }}",
        )
