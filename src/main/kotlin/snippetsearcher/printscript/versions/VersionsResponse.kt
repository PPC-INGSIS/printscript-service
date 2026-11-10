package snippetsearcher.printscript.versions

data class VersionsResponse(
    val versions: List<VersionInfo>,
    val latest: String,
)

data class VersionInfo(
    val id: String,
)
