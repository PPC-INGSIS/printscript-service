package snippetsearcher.printscript.validation

import org.printscript.common.Version

class UnsupportedVersionException(
    versionId: String,
) : RuntimeException(
        "La versión '$versionId' no existe. Versiones disponibles: ${Version.entries.joinToString { it.id }}",
    )
