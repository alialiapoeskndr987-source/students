package com.yousef.stephealth.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ألوان الهوية مستمدة من أيقونة التطبيق (كحلي + برتقالي + فيروزي)
val Navy = Color(0xFF16305E)
val NavyLight = Color(0xFF274690)
val Orange = Color(0xFFF59E6B)
val OrangeDeep = Color(0xFFE8793A)
val Teal = Color(0xFF2DD4BF)
val Cream = Color(0xFFFBF9F6)
val Ink = Color(0xFF1E293B)
val SlateGray = Color(0xFF64748B)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = NavyLight,
    onPrimaryContainer = Color.White,
    secondary = OrangeDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEDD5),
    onSecondaryContainer = Color(0xFF9A3412),
    tertiary = Teal,
    background = Cream,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = SlateGray,
    error = Color(0xFFDC2626)
)

@Composable
fun StepHealthTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // المرحلة الأولى: مظهر نهاري ثابت بألوان الهوية (الوضع الليلي في مرحلة اللمسات النهائية)
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
