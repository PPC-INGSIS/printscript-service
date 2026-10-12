package snippetsearcher.printscript.source

import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile

// Reemplazo provisorio del Snippet Store: cada ruta es un archivo dentro de una carpeta local.
class LocalSnippetReader(
    directory: Path,
) : SnippetReader {
    private val root = directory.toAbsolutePath().normalize()

    override fun open(path: String): InputStream {
        val file = root.resolve(path).normalize()
        // Con .. o una ruta absoluta el archivo queda fuera de root: se trata como inexistente
        if (!file.startsWith(root) || !file.isRegularFile()) throw SnippetNotFoundException(path)
        return file.inputStream()
    }
}
