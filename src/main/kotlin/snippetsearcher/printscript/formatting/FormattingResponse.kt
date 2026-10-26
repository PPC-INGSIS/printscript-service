package snippetsearcher.printscript.formatting

import snippetsearcher.printscript.common.SourceError

// Si hubo error, formatted es null: lo formateado hasta ahí no sirve como snippet
data class FormattingResponse(
    val formatted: String?,
    val error: SourceError?,
)
