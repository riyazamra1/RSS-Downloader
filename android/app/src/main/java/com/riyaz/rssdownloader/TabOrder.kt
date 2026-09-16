package com.riyaz.rssdownloader

object TabOrder {
    const val SOCIAL = "social-downloader"
    const val TAMIL = "tamil-movies"
    const val DUBBED = "tamil-dubbed-movies"

    val defaults = listOf(SOCIAL, TAMIL, DUBBED)

    fun load(raw: String?): MutableList<String> {
        val saved = raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotBlank() }
        val valid = saved.filter { it in defaults }.distinct().toMutableList()
        defaults.forEach { if (it !in valid) valid += it }
        return valid
    }

    fun save(order: List<String>): String = load(order.joinToString(",")).joinToString(",")
}
