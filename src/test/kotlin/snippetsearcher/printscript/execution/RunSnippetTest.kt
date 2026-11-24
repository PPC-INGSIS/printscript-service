package snippetsearcher.printscript.execution

import org.junit.jupiter.api.Test
import org.printscript.common.Version
import org.printscript.interpreter.io.PrintScriptIO
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RunSnippetTest {
    @Test
    fun `una excepción nueva que extiende ExecutionAborted vuelve como error sin posición`() {
        val error = runSnippet("let nombre: string = readInput(\"Nombre: \");", Version.V11, AbortingIo)

        assertEquals("Corte de un IO nuevo", assertNotNull(error).message)
        assertNull(error.line)
        assertNull(error.column)
    }
}

private class NewIoAbortedException : ExecutionAborted("Corte de un IO nuevo")

private object AbortingIo : PrintScriptIO {
    override fun print(message: String) = Unit

    override fun read(prompt: String): String = throw NewIoAbortedException()

    override fun env(name: String): String? = null
}
