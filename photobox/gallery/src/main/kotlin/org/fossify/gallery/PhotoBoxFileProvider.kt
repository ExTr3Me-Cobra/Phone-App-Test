package org.fossify.gallery

import androidx.core.content.FileProvider

/**
 * PhotoBox: Gallery's file sharing provider. Image Toolbox already declares the plain
 * androidx FileProvider, and one app can't declare the same class twice, so Gallery uses this.
 */
class PhotoBoxFileProvider : FileProvider()
