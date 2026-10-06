package vn.sysclean.core.privilege.shell

/**
 * Builds the scripts handed to `su -c`. Everything that reaches the root shell passes
 * through here: paths are checked against [PrivilegedPaths] and every argument is quoted,
 * so a file name such as `x'; rm -rf / #` stays a harmless string.
 */
object RootCommands {

    fun quote(argument: String): String = "'" + argument.replace("'", "'\\''") + "'"

    fun command(arguments: List<String>): String {
        require(PrivilegedPaths.isCommandAllowed(arguments)) {
            "command not allowed: ${arguments.firstOrNull()}"
        }
        return arguments.joinToString(" ") { quote(it) }
    }

    fun list(path: String): String {
        require(PrivilegedPaths.isUnderPrivilegedRoot(path)) { "not listable: $path" }
        return "ls -1A ${quote(path)}"
    }

    /** Prints the size in KiB; the root shell has no portable byte-exact du. */
    fun sizeOf(path: String): String {
        require(PrivilegedPaths.isUnderPrivilegedRoot(path) || PrivilegedPaths.isInternalCacheDir(path)) { "not measurable: $path" }
        return "du -sk ${quote(path)} 2>/dev/null | cut -f1"
    }

    /** Prints the size in KiB before emptying, so the caller knows what was freed. */
    fun deleteContents(path: String): String {
        require(PrivilegedPaths.isDeletable(path) || PrivilegedPaths.isInternalCacheDir(path)) { "not deletable: $path" }
        val target = quote(path)
        return "[ -d $target ] || { echo 0; exit 0; }; du -sk $target | cut -f1; " +
            "find $target -mindepth 1 -maxdepth 1 -exec rm -rf {} +"
    }

    fun delete(path: String): String {
        require(PrivilegedPaths.isDeletable(path)) { "not deletable: $path" }
        return "rm -rf ${quote(path)}"
    }

    fun parseKib(output: String): Long = (output.lineSequence().firstOrNull()?.trim()?.toLongOrNull() ?: 0) * 1024
}
