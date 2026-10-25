package com.timebox.android.ui

internal data class ShortcutSlide(val from: String, val to: String, val direction: Int)

/** Only ordered shortcuts imply adjacent pages. Directory and contextual links do not. */
internal fun shortcutSlide(shortcuts: List<String>, from: String, to: String): ShortcutSlide? {
    val source = shortcuts.indexOf(from)
    val target = shortcuts.indexOf(to)
    if (source < 0 || target < 0 || source == target) return null
    return ShortcutSlide(from, to, if (target > source) 1 else -1)
}
