package snippetsearcher.printscript.execution

class OutputTooLargeException(
    max: Long,
) : RuntimeException("La salida superó el máximo de $max caracteres")
