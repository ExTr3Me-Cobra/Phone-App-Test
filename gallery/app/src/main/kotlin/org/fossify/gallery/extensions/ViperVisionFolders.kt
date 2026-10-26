package org.fossify.gallery.extensions

import android.app.Activity
import android.content.Context
import org.fossify.commons.dialogs.ConfirmationDialog
import com.google.gson.Gson
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.toast
import org.fossify.gallery.R
import org.fossify.gallery.models.AlbumCover

/**
 * Viper Vision: makes the photo at [path] its folder's thumbnail on the folders screen (the same
 * album cover Gallery's folder menu sets).
 */
fun Context.setAsFolderCover(path: String) {
    val folder = path.getParentPath()
    val covers = config.parseAlbumCovers().filterNot { it.path == folder } as ArrayList<AlbumCover>
    covers.add(AlbumCover(folder, path))
    config.albumCovers = Gson().toJson(covers)
    toast(R.string.vv_folder_cover_set)
}

/**
 * Viper Vision: hiding always asks twice, "Hide [what]?" and then "Are you really sure?", before
 * [onConfirmed] runs.
 */
fun Activity.confirmHideTwice(what: String, onConfirmed: () -> Unit) {
    ConfirmationDialog(this, getString(R.string.vv_hide_confirm, what), 0, org.fossify.commons.R.string.yes, org.fossify.commons.R.string.no) {
        ConfirmationDialog(this, getString(R.string.vv_hide_confirm_again, what), 0, org.fossify.commons.R.string.yes, org.fossify.commons.R.string.no) {
            onConfirmed()
        }
    }
}
