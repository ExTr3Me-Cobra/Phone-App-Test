package org.fossify.gallery.extensions

import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.helpers.LOWER_ALPHA
import android.widget.TextView
import org.fossify.commons.views.MySearchMenu

/**
 * Viper Vision: Gallery's top bar without file-name search. The search field is hidden and the
 * button on the left (where the search icon was) does [onClick], with [label] next to it, e.g.
 * the switch between the photo grid and folders ("Grid view" / "File view"). The ⋮ menu on the
 * right stays.
 */
fun MySearchMenu.useViperVisionBar(icon: Int?, label: String, onClick: () -> Unit) {
    binding.topToolbarSearch.apply {
        visibility = View.INVISIBLE
        isEnabled = false
        isFocusable = false
        isFocusableInTouchMode = false
    }
    binding.topToolbarSearchIcon.apply {
        icon?.let(::setImageResource)
        contentDescription = label
        setOnClickListener { onClick() }
    }
    // The pill background goes only around the button and its label, not the whole bar.
    val container = binding.toolbarContainer
    val pill = container.findViewWithTag(PILL_TAG) ?: LinearLayout(context).also { row ->
        row.tag = PILL_TAG
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val icon = binding.topToolbarSearchIcon
        (icon.parent as? ViewGroup)?.removeView(icon)
        row.addView(icon, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))
        row.addView(TextView(context).apply {
            tag = LABEL_TAG
            gravity = Gravity.CENTER_VERTICAL
            // Two short lines ("Go to" / "album view"), so a bit smaller than the search text.
            setTextSize(TypedValue.COMPLEX_UNIT_PX, binding.topToolbarSearch.textSize * 0.8f)
            setLineSpacing(0f, 0.9f)
            setPadding(binding.topToolbarSearch.paddingStart, 0, binding.topToolbarSearch.paddingStart, 0)
            maxLines = 2
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))
        container.addView(row, RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        ).apply {
            addRule(RelativeLayout.ALIGN_PARENT_START)
            addRule(RelativeLayout.CENTER_VERTICAL)
        })
    }
    // Same colours updateColors gives the search bar, on the pill instead of the whole bar.
    container.background = null
    pill.background = ContextCompat.getDrawable(context, org.fossify.commons.R.drawable.search_menu_background)
        ?.mutate()
        ?.apply { applyColorFilter(context.getProperPrimaryColor().adjustAlpha(LOWER_ALPHA)) }
    val text = pill.findViewWithTag<TextView>(LABEL_TAG)
    text.text = label
    text.setTextColor(binding.topToolbarSearch.currentTextColor)
    pill.setOnClickListener { onClick() }
    text.setOnClickListener { onClick() }
}

/**
 * Viper Vision: the top bar keeps only [keep] (e.g. Sort) and Settings, which shows as ⋮ and
 * opens Settings straight away (Group by and the camera shortcut are in Settings). Everything else
 * is hidden, including items the code may show again, so call this after it updates the menu.
 */
fun Menu.keepOnlyViperVisionItems(settingsId: Int, vararg keep: Int) {
    for (i in 0 until size()) {
        val item = getItem(i)
        if (item.itemId != settingsId && item.itemId !in keep) item.isVisible = false
    }
    findItem(settingsId)?.isVisible = true
}

/** Viper Vision: Settings as the ⋮ button (call once, right after inflating the menu). */
fun Menu.settingsAsDotsButton(settingsId: Int) {
    findItem(settingsId)?.apply {
        setIcon(org.fossify.commons.R.drawable.ic_three_dots_vector)
        setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
    }
}

private const val LABEL_TAG = "vv_top_bar_label"
private const val PILL_TAG = "vv_top_bar_pill"
