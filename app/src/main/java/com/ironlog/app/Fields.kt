package com.ironlog.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A small number box for set tables. Keeps what you type while you type it, and reports the
 * parsed value (null when empty). [placeholder] shows in grey when empty.
 */
@Composable
fun NumField(
    initial: String,
    placeholder: String,
    key: Any,
    modifier: Modifier,
    keyboard: KeyboardType = KeyboardType.Decimal,
    background: Color = Color.Transparent,
    outlined: Boolean = false,
    onChange: (String) -> Unit,
) {
    var text by remember(key) { mutableStateOf(initial) }
    val shape = RoundedCornerShape(if (outlined) 10.dp else 6.dp)
    Box(
        modifier.clip(shape).background(background)
            .then(if (outlined) Modifier.border(1.dp, Color(0xFF3A3A3C), shape) else Modifier)
            .padding(horizontal = 6.dp, vertical = if (outlined) 12.dp else 4.dp),
        contentAlignment = if (outlined) Alignment.Center else Alignment.CenterStart,
    ) {
        val align = if (outlined) TextAlign.Center else TextAlign.Start
        if (text.isEmpty()) Text(placeholder, color = Sub, fontSize = 17.sp, textAlign = align, modifier = if (outlined) Modifier.fillMaxWidth() else Modifier)
        BasicTextField(
            value = text,
            onValueChange = { t ->
                val clean = t.filter { it.isDigit() || it == '.' || it == ':' }.take(8)
                text = clean
                onChange(clean)
            },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, textAlign = align),
            cursorBrush = SolidColor(Blue),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = if (outlined) Modifier.fillMaxWidth() else Modifier,
        )
    }
}
