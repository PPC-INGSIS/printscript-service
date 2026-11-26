package snippetsearcher.printscript.execution

import org.junit.jupiter.api.Test
import org.printscript.common.Version
import org.printscript.interpreter.io.PrintScriptIO
import snippetsearcher.printscript.common.SourceError
import snippetsearcher.printscript.common.deeplyNestedCode
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

    @Test
    fun `un código con demasiado anidamiento vuelve con el error de anidamiento`() {
        val error = runSnippet(deeplyNestedCode, Version.V11, CollectingIo(emptyList(), Long.MAX_VALUE))

        val expected = SourceError("El código tiene demasiados niveles de anidamiento", line = null, column = null)
        assertEquals(expected, error)
    }
}

private class NewIoAbortedException : ExecutionAborted("Corte de un IO nuevo")

private object AbortingIo : PrintScriptIO {
    override fun print(message: String) = Unit

    override fun read(prompt: String): String = throw NewIoAbortedException()

    override fun env(name: String): String? = null
}
