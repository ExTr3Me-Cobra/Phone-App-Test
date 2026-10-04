package com.webshortcuts.app

import android.net.Uri

private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
private val HOST_NAME = Regex("^([\\p{L}\\p{N}]([\\p{L}\\p{N}-]*[\\p{L}\\p{N}])?\\.)+[\\p{L}]{2,}$")
private val IPV4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

/**
 * Cleans up what the user typed into a full web address, adding https:// when no scheme is
 * given. Returns null if it isn't a valid http(s) address.
 */
fun normalizeUrl(input: String): String? {
    val text = input.trim()
    if (text.isEmpty() || text.any { it.isWhitespace() }) return null
    val full = if (SCHEME.containsMatchIn(text)) text else "https://$text"
    val uri = Uri.parse(full)
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host ?: return null
    val validHost = host.equals("localhost", ignoreCase = true) ||
        HOST_NAME.matches(host) ||
        IPV4.matchEntire(host)?.groupValues?.drop(1)?.all { it.toInt() in 0..255 } == true
    return if (validHost) full else null
}

/** A sensible label from an address: "https://www.example.com/x" -> "example.com". */
fun defaultLabel(url: String): String =
    Uri.parse(url).host?.removePrefix("www.")?.takeIf { it.isNotEmpty() } ?: "Website"
