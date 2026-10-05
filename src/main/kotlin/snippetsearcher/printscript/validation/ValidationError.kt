package snippetsearcher.printscript.validation

// Dónde empieza el error. Línea y columna tal como las da la librería: base 1
data class ValidationError(
    val message: String,
    val line: Int,
    val column: Int,
)
