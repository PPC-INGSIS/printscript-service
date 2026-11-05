package snippetsearcher.printscript.formatting

import snippetsearcher.printscript.common.SourceError

// Si hubo error, formatted es null: lo formateado hasta ahí no sirve como snippet
data class FormattingResponse(
    val formatted: String?,
    val error: SourceError?,
    // Las completa el controller: el service recibe la config ya resuelta
    val ignoredRules: List<String> = emptyList(),
)
