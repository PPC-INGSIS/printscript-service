package snippetsearcher.printscript.common

// Dónde empieza el error. Línea y columna tal como las da la librería: base 1
data class SourceError(
    val message: String,
    val line: Int,
    val column: Int,
)
