package snippetsearcher.printscript.common

import org.printscript.common.Version

fun resolveVersion(id: String): Version = Version.of(id) ?: throw UnsupportedVersionException(id)
