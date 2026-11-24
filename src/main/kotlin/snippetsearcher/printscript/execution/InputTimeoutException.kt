package snippetsearcher.printscript.execution

import snippetsearcher.printscript.common.describe
import java.time.Duration

class InputTimeoutException(
    timeout: Duration,
) : ExecutionAborted("No se recibió un input en ${timeout.describe()}")
