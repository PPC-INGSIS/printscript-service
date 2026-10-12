package snippetsearcher.printscript.source

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals

class LocalSnippetReaderTest {
    @TempDir
    private lateinit var root: Path

    private val store by lazy { root.resolve("store").createDirectories() }
    private val reader by lazy { LocalSnippetReader(store) }

    @Test
    fun `lee el snippet guardado en la ruta`() {
        store.resolve("snippets").createDirectories().resolve("7").writeText("println(1);")

        val content = reader.open("snippets/7").use { it.readAllBytes().decodeToString() }

        assertEquals("println(1);", content)
    }

    @Test
    fun `una ruta que no existe es un snippet inexistente`() {
        val exception = assertThrows<SnippetNotFoundException> { reader.open("snippets/no-existe") }

        assertEquals("No hay ningún snippet en la ruta 'snippets/no-existe'", exception.message)
    }

    @Test
    fun `una carpeta no es un snippet`() {
        store.resolve("snippets").createDirectories()

        assertThrows<SnippetNotFoundException> { reader.open("snippets") }
    }

    @Test
    fun `no deja salir de la carpeta con dos puntos`() {
        root.resolve("secreto.txt").writeText("no")

        assertThrows<SnippetNotFoundException> { reader.open("../secreto.txt") }
    }

    @Test
    fun `no deja leer rutas absolutas`() {
        val outside = root.resolve("secreto.txt").also { it.writeText("no") }

        assertThrows<SnippetNotFoundException> { reader.open(outside.toString()) }
    }
}
