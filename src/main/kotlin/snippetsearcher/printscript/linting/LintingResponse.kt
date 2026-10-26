package snippetsearcher.printscript.linting

// Lista vacía = sin violaciones. Los errores de sintaxis vienen acá también, con rule "syntax"
data class LintingResponse(
    val violations: List<LintViolation>,
)
