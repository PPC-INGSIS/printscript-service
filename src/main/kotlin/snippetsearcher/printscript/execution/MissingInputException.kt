package snippetsearcher.printscript.execution

// Snippets pasa el mensaje tal cual al usuario, así que tiene que leerse bien con 0, 1 o más inputs
class MissingInputException(
    sent: Int,
) : RuntimeException("Faltan inputs: el snippet pidió más de los que se enviaron (${describe(sent)})")

private fun describe(sent: Int) =
    when (sent) {
        0 -> "no se envió ninguno"
        1 -> "se envió 1"
        else -> "se enviaron $sent"
    }
