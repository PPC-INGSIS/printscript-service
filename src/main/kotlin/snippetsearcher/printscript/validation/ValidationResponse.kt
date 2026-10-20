package snippetsearcher.printscript.validation

import snippetsearcher.printscript.common.SourceError

// Lista vacía = el código es válido
data class ValidationResponse(
    val errors: List<SourceError>,
)
