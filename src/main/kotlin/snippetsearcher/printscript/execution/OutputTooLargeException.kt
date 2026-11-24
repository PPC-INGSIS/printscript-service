package snippetsearcher.printscript.execution

class OutputTooLargeException(
    max: Long,
) : ExecutionAborted("La salida superó el máximo de $max caracteres")
