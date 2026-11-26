package snippetsearcher.printscript.linting

import snippetsearcher.printscript.common.SourceError

// Lista vacía = sin violaciones. Los errores de sintaxis vienen en violations, con rule "syntax"
data class LintingResponse(
    val violations: List<LintViolation>,
    val error: SourceError? = null,
    // Las completa el controller: el service recibe la config ya resuelta
    val ignoredRules: List<String> = emptyList(),
)
