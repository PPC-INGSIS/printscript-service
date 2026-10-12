package snippetsearcher.printscript.execution

class MissingInputException(
    sent: Int,
) : RuntimeException("Faltan inputs: el snippet pidió más de los $sent enviados")
