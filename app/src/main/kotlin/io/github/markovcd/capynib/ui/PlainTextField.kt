package io.github.markovcd.capynib.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle

/** A text field with no box or underline: the text sits on the note or bar it belongs to. */
@Composable
fun PlainTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    readOnly: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = style.copy(color = colors.onSurface),
        singleLine = singleLine,
        readOnly = readOnly,
        cursorBrush = SolidColor(colors.primary),
        decorationBox = { field ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style, color = colors.onSurfaceVariant, maxLines = 1)
                field()
            }
        },
    )
}
