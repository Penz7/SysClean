package vn.sysclean.feature.cleaner

import vn.sysclean.core.model.JunkCategory
import vn.sysclean.core.model.JunkItem

/**
 * What is ticked when a category opens. Safe junk is pre-selected, byte-identical duplicates
 * pre-select every copy but one, and anything that needs judgement (similar or blurry photos,
 * large files, screenshots) starts empty so nothing personal is removed by accident.
 */
internal fun defaultSelection(category: JunkCategory, items: List<JunkItem>): Set<String> = when {
    category == JunkCategory.DUPLICATE_FILES -> extrasOf(items)
    // With Shizuku, apps whose clearable cache is 0 B would only inflate the count.
    category == JunkCategory.APP_CACHE -> items.filter { it.sizeBytes > 0 }.map { it.path }.toSet()
    category.isSafeToClean -> items.map { it.path }.toSet()
    else -> emptySet()
}

/** Every member of every set except the one marked to keep. */
internal fun extrasOf(items: List<JunkItem>): Set<String> =
    items.filter { it.groupId != null && !it.isOriginal }.map { it.path }.toSet()
