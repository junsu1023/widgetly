package com.example.widgetly.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Material3's `Shapes.small` (used elsewhere to style e.g. OutlinedTextField)
 * cannot carry this value to a `Button`: `ButtonDefaults.shape` resolves to a
 * hardcoded `CircleShape` regardless of what `Shapes.small` is set to (M3
 * reserves the "full" corner token for buttons and never reads the custom
 * Shapes object for it). Every Button in the app passes this explicitly
 * instead - defined once here (shared-core, not :shared, so both modules'
 * screens can reference the same value) rather than repeating the literal
 * `14.dp` at each call site.
 */
val WidgetlyButtonShape = RoundedCornerShape(14.dp)
