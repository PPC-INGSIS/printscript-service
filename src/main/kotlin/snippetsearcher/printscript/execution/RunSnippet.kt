package snippetsearcher.printscript.execution

import org.printscript.common.Result
import org.printscript.common.Version
import org.printscript.interpreter.io.PrintScriptIO
import org.printscript.lexer.source.StringSourceReader
import org.printscript.runner.ExecuteRunner
import snippetsearcher.printscript.common.SourceError
import snippetsearcher.printscript.common.handlingDeepNesting
import snippetsearcher.printscript.common.toSourceError

fun runSnippet(
    code: String,
    version: Version,
    io: PrintScriptIO,
): SourceError? =
    handlingDeepNesting({ it }) {
        try {
            when (val result = ExecuteRunner(version, io).execute { StringSourceReader(code) }) {
                is Result.Success -> null
                is Result.Failure -> result.error.toSourceError()
            }
        } catch (exception: ExecutionAborted) {
            // Sin posición: ni read ni print reciben el rango de la sentencia y ExecuteRunner no atrapa
            // la excepción, así que cuando llega acá ya no hay forma de saber dónde estaba
            SourceError(exception.message.orEmpty(), line = null, column = null)
        }
    }
