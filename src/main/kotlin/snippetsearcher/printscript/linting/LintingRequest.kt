package snippetsearcher.printscript.linting

// Igual que /format: el código llega en el body y rules usa los ids de reglas del servicio,
// con las claves de la librería como alias deprecados. {} aplica los defaults.
data class LintingRequest(
    val code: String,
    val version: String,
    val rules: Map<String, Any?>,
)
