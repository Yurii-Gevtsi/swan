package com.gysignalstudio.blackswan.ui

import com.gysignalstudio.blackswan.data.model.SourceEntity
import java.net.URI

private val cyrillicRegex = Regex("\\p{IsCyrillic}")
private val wikiNoteRegex = Regex("Wikipedia note #(\\d+)", RegexOption.IGNORE_CASE)

private fun containsCyrillic(text: String): Boolean = cyrillicRegex.containsMatchIn(text)

private fun sourceHostLabel(url: String): String {
    return runCatching { URI(url).host.orEmpty() }
        .getOrDefault("")
        .removePrefix("www.")
        .ifBlank { "External source" }
}

fun localizedSourceName(source: SourceEntity, isUk: Boolean): String {
    if (isUk || !containsCyrillic(source.sourceName)) return source.sourceName

    val publisherLabel = source.publisher
        .takeIf { it.isNotBlank() && !containsCyrillic(it) }
        ?: sourceHostLabel(source.sourceUrl)
    val wikiNote = wikiNoteRegex.find(source.sourceDescription)?.groupValues?.getOrNull(1)

    return if (wikiNote != null) {
        "$publisherLabel reference (Wikipedia note #$wikiNote)"
    } else {
        "$publisherLabel reference"
    }
}

fun localizedPublisher(source: SourceEntity, isUk: Boolean): String {
    return if (isUk || !containsCyrillic(source.publisher)) {
        source.publisher
    } else {
        sourceHostLabel(source.sourceUrl)
    }
}
