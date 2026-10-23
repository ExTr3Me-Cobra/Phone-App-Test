package org.fossify.commons.helpers

/**
 * PhotoBox: the package Gallery's classes (and launcher icon aliases) live in.
 *
 * Commons used to build these class names from the app ID, which only works while the app ID is
 * "org.fossify.gallery". PhotoBox has its own app ID, so the class names are built from this.
 *
 * Other PhotoBox changes in Commons: the checks that treated any app whose ID doesn't start with
 * "org.fossify." as a fake copy (random warning pop-ups, a blocked theme screen after 50-100
 * launches, skipped SD card permission prompts) are removed. Search for "PhotoBox" to find them.
 */
const val LAUNCHER_ALIAS_PACKAGE = "org.fossify.gallery"
