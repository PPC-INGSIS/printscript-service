package snippetsearcher.printscript.source

class SnippetNotFoundException(
    path: String,
) : RuntimeException("No hay ningún snippet en la ruta '$path'")
