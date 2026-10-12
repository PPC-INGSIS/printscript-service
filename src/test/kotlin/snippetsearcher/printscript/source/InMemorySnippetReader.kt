package snippetsearcher.printscript.source

import java.io.ByteArrayInputStream
import java.io.InputStream

// Reemplaza al Snippet Store en los tests: ruta → contenido. Registra qué streams se cerraron.
class InMemorySnippetReader(
    private val snippets: Map<String, String>,
) : SnippetReader {
    private val opened = mutableListOf<TrackedStream>()

    val allClosed: Boolean
        get() = opened.isNotEmpty() && opened.all { it.closed }

    override fun open(path: String): InputStream {
        val content = snippets[path] ?: throw SnippetNotFoundException(path)
        return TrackedStream(content.toByteArray()).also { opened += it }
    }

    private class TrackedStream(
        bytes: ByteArray,
    ) : ByteArrayInputStream(bytes) {
        var closed = false

        override fun close() {
            closed = true
            super.close()
        }
    }

    companion object {
        // Los snippets de src/test/resources/snippets, cada uno en la ruta snippets/<nombre>
        fun fromResources(vararg names: String) =
            InMemorySnippetReader(
                names.associate { "snippets/$it" to javaClass.getResource("/snippets/$it")!!.readText() },
            )
    }
}
