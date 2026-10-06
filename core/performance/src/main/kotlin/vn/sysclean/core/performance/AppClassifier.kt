package vn.sysclean.core.performance

import vn.sysclean.core.privilege.shell.BloatwarePolicy

/** Decides what SysClean may do with an app holding RAM. Pure, so the safety rules are tested. */
object AppClassifier {
    fun kindOf(
        packageName: String,
        isUserApp: Boolean,
        inActiveUse: Set<String>,
        unusedPreinstalled: Set<String> = emptySet(),
    ): AppKind = when {
        packageName in inActiveUse || BloatwarePolicy.isProtected(packageName, inActiveUse) -> AppKind.LOCKED
        packageName in ResidentServices.optional -> AppKind.OPTIONAL_SERVICE
        isUserApp -> AppKind.USER_APP
        packageName in unusedPreinstalled -> AppKind.IDLE_PRELOAD
        else -> AppKind.SYSTEM_APP
    }

    /** For an app with several processes, the most active one decides its group. */
    fun mostActive(groups: Collection<MemoryGroup>): MemoryGroup = when {
        MemoryGroup.ACTIVE in groups -> MemoryGroup.ACTIVE
        MemoryGroup.BACKGROUND in groups -> MemoryGroup.BACKGROUND
        MemoryGroup.SYSTEM in groups -> MemoryGroup.SYSTEM
        else -> MemoryGroup.CACHED
    }
}
