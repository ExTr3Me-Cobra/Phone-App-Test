package com.t8rin.imagetoolbox.core.data.remote

import io.ktor.http.Url

/**
 * Viper Vision: the only places the app may download from: its AI models (its own copy in the
 * app's GitHub release first, see mirrorUrl, else the models' original pages), and the download
 * servers those send the request on to. Everything else (Image Toolbox's add-on packs from its
 * author's GitHub, pictures from web links, ...) is refused. Enforced on the app's one web
 * client (RemoteModule.client).
 */
internal object NetworkAllowList {

    private val prefixes = listOf(
        "https://github.com/ExTr3Me-Cobra/Phone-App-Test/releases/download/vipervision-ai-models/",
        "https://huggingface.co/T8RIN/imagetoolbox-models/resolve/",
        "https://huggingface.co/onnx-community/siglip2-base-patch16-256-ONNX/resolve/",
        "https://huggingface.co/chongzhou/EdgeSAM/resolve/",
        "https://github.com/facefusion/facefusion-assets/releases/download/"
    )

    /** Where GitHub and Hugging Face send downloads on to. */
    private val downloadHosts = listOf("objects.githubusercontent.com", "release-assets.githubusercontent.com")
    private val downloadHostSuffixes = listOf(".huggingface.co", ".hf.co")

    fun allows(url: Url): Boolean {
        if (url.protocol.name != "https") return false
        val host = url.host.lowercase()
        if (host in downloadHosts || downloadHostSuffixes.any { host.endsWith(it) }) return true
        val text = url.toString()
        return prefixes.any { text.startsWith(it) }
    }
}
