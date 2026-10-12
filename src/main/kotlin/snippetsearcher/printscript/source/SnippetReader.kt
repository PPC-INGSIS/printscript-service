package snippetsearcher.printscript.source

import java.io.InputStream

// Lee el código de un snippet ya guardado en el Snippet Store. Quien llama cierra el stream.
// Si la ruta no existe, lanza SnippetNotFoundException.
fun interface SnippetReader {
    fun open(path: String): InputStream
}
