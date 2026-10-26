package com.t8rin.imagetoolbox.core.data.saving

import com.t8rin.imagetoolbox.core.domain.saving.RandomStringGenerator
import java.security.SecureRandom
import javax.inject.Inject

/**
 * Viper Vision: moved here from Image Toolbox's Cipher tool (removed), which used to provide it.
 * Saving files still needs it for random filenames.
 */
internal class AndroidRandomStringGenerator @Inject constructor() : RandomStringGenerator {

    override fun generate(length: Int): String {
        val random = SecureRandom()
        return buildString {
            repeat(length) {
                append(CHARS[random.nextInt(CHARS.length)])
            }
        }
    }

    private companion object {
        const val CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    }
}
