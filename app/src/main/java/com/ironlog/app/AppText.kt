package com.ironlog.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Developer: edit any text in the app right where it's shown. Changes show straight away (everywhere that exact text
 * appears) and can be exported as a list of "was → now" to build into the app.
 */
object AppText {
    private const val FILE = "app_text.json"
    /** Original text → your version. */
    val edits = mutableStateMapOf<String, String>()
    /** The setting: every text becomes editable. */
    var on by mutableStateOf(false)
        private set
    /** Paused from the floating pill, so you can tap around normally. */
    var paused by mutableStateOf(false)
    val active get() = on && !paused
    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        runCatching {
            val o = JSONObject(Store.readFile(FILE) ?: "{}")
            on = o.optBoolean("on")
            o.optJSONArray("edits")?.let { a ->
                for (i in 0 until a.length()) a.getJSONObject(i).let { edits[it.getString("was")] = it.getString("now") }
            }
        }
    }

    private fun save() = Store.writeFile(FILE, JSONObject().put("on", on).put("edits", export()).toString())

    fun setOn(v: Boolean) { on = v; paused = false; save() }

    fun set(original: String, now: String) {
        if (now == original) edits.remove(original) else edits[original] = now
        save()
    }

    fun clear() { edits.clear(); save() }

    /** Every change: what it said, and what you want it to say. */
    fun export(): JSONArray = JSONArray(edits.entries.sortedBy { it.key }.map { JSONObject().put("was", it.key).put("now", it.value) })
}

/** Text shown everywhere in the app: your edited version if you changed it, and editable in place while editing is on. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    val shown = AppText.edits[text] ?: text
    if (AppText.active) EditableText(text, shown, modifier, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing,
        textDecoration, textAlign, lineHeight, maxLines, minLines, style)
    else androidx.compose.material3.Text(text = shown, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
            fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight,
            overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout ?: {}, style = style)
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    val edited = AppText.edits[text.text]
    when {
        AppText.active -> EditableText(text.text, edited ?: text.text, modifier, color, fontSize, fontStyle, fontWeight, fontFamily,
            letterSpacing, textDecoration, textAlign, lineHeight, maxLines, minLines, style)
        // Your version is plain text (the original's coloured parts, like stars, aren't kept).
        edited != null -> androidx.compose.material3.Text(text = edited, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
            fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight,
            overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout, style = style)
        else -> androidx.compose.material3.Text(text = text, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
            fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight,
            overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, inlineContent = inlineContent, onTextLayout = onTextLayout, style = style)
    }
}

/** The same text, as a box you can type in. Blue outline = editable; green = already changed. */
@Composable
private fun EditableText(
    original: String, shown: String, modifier: Modifier, color: Color, fontSize: TextUnit, fontStyle: FontStyle?, fontWeight: FontWeight?,
    fontFamily: FontFamily?, letterSpacing: TextUnit, textDecoration: TextDecoration?, textAlign: TextAlign?, lineHeight: TextUnit,
    maxLines: Int, minLines: Int, style: TextStyle,
) {
    var value by remember(original) { mutableStateOf(shown) }
    val changed = value != original
    val tint = if (changed) Green else Blue
    val textColor = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    BasicTextField(
        value, { v -> value = v; AppText.set(original, v) },
        modifier.border(1.dp, tint.copy(alpha = 0.7f), RoundedCornerShape(4.dp)).background(tint.copy(alpha = 0.10f), RoundedCornerShape(4.dp)),
        textStyle = style.merge(TextStyle(
            color = textColor, fontSize = fontSize, fontWeight = fontWeight, textAlign = textAlign ?: TextAlign.Unspecified, fontStyle = fontStyle,
            fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, lineHeight = lineHeight,
        )),
        maxLines = maxLines, minLines = minLines,
        cursorBrush = SolidColor(tint),
    )
}

/** While text editing is on: a small pill at the top to pause it (so taps work normally) or carry on. */
@Composable
fun AppTextPill() {
    if (!AppText.on) return
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 60.dp), contentAlignment = Alignment.TopCenter) {
        androidx.compose.material3.Text(
            if (AppText.paused) "⏸ Text editing paused · tap to edit" else "✏️ Editing text (${AppText.edits.size} changed) · tap to pause",
            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (AppText.paused) Card2 else Blue.copy(alpha = 0.92f))
                .clickable { AppText.paused = !AppText.paused }.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}
