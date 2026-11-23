package snippetsearcher.printscript.formatting

// El código llega en el body, como en /execute: leerlo del store es responsabilidad de Snippets.
// rules usa los ids de reglas del servicio. {} aplica los defaults.
data class FormattingRequest(
    val code: String,
    val version: String,
    val rules: Map<String, Any?>,
)
