package snippetsearcher.printscript.linting

// Igual que /format: el código llega en el body y rules usa las claves del archivo de configuración de la librería
data class LintingRequest(
    val code: String,
    val version: String,
    val rules: Map<String, Any?>,
)
