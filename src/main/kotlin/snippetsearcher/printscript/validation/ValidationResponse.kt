package snippetsearcher.printscript.validation

// Lista vacía = el código es válido
data class ValidationResponse(
    val errors: List<ValidationError>,
)
