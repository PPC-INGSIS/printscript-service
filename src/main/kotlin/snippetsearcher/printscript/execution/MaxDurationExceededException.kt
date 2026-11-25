package snippetsearcher.printscript.execution

import snippetsearcher.printscript.common.describe
import java.time.Duration

class MaxDurationExceededException(
    maxDuration: Duration,
) : ExecutionAborted("La ejecución superó la duración máxima de ${maxDuration.describe()}")
