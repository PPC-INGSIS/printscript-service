package snippetsearcher.printscript.versions

import org.printscript.common.Version
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class VersionsController {
    @GetMapping("/versions")
    fun versions(): VersionsResponse =
        VersionsResponse(
            versions = Version.entries.map { VersionInfo(it.id) },
            latest = Version.entries.last().id,
        )
}
