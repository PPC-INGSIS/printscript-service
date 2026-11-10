package snippetsearcher.printscript.versions

import org.printscript.common.Version
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import snippetsearcher.printscript.common.latestVersion

@RestController
class VersionsController {
    @GetMapping("/versions")
    fun versions(): VersionsResponse =
        VersionsResponse(
            versions = Version.entries.map { VersionInfo(it.id) },
            latest = latestVersion().id,
        )
}
