package ir.pingscore.app.ui

import android.graphics.Typeface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF042F2E),
    background = Color(0xFFF8FAFB),
    surface = Color(0xFFF8FAFB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF115E59),
    onPrimaryContainer = Color(0xFFCCFBF1),
    background = Color(0xFF0E1415),
    surface = Color(0xFF0E1415),
)

/** رنگ‌های معنایی نمره. */
object ScoreColors {
    val good = Color(0xFF16A34A)
    val medium = Color(0xFFD97706)
    val poor = Color(0xFFDC2626)
}

/**
 * فونت وزیرمتن: فایل Vazirmatn-Regular.ttf را در app/src/main/assets/fonts/ بگذارید.
 * اگر فایل نبود، برنامه با فونت سیستم کار می‌کند (کامپایل هم نمی‌شکند).
 */
@Composable
private fun rememberFontFamily(): FontFamily {
    val context = LocalContext.current
    return remember {
        try {
            val files = context.assets.list("fonts")?.toSet().orEmpty()
            if ("Vazirmatn-Regular.ttf" in files) {
                FontFamily(Typeface.createFromAsset(context.assets, "fonts/Vazirmatn-Regular.ttf"))
            } else {
                FontFamily.Default
            }
        } catch (e: Exception) {
            FontFamily.Default
        }
    }
}

private fun Typography.withFont(font: FontFamily): Typography = copy(
    displayLarge = displayLarge.withFont(font), displayMedium = displayMedium.withFont(font),
    displaySmall = displaySmall.withFont(font), headlineLarge = headlineLarge.withFont(font),
    headlineMedium = headlineMedium.withFont(font), headlineSmall = headlineSmall.withFont(font),
    titleLarge = titleLarge.withFont(font), titleMedium = titleMedium.withFont(font),
    titleSmall = titleSmall.withFont(font), bodyLarge = bodyLarge.withFont(font),
    bodyMedium = bodyMedium.withFont(font), bodySmall = bodySmall.withFont(font),
    labelLarge = labelLarge.withFont(font), labelMedium = labelMedium.withFont(font),
    labelSmall = labelSmall.withFont(font),
)

private fun TextStyle.withFont(font: FontFamily): TextStyle = copy(fontFamily = font)

/** تم برنامه؛ جهت همه‌ی صفحه‌ها همیشه راست‌به‌چپ است. */
@Composable
fun PingScoreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val font = rememberFontFamily()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography().withFont(font),
            content = content,
        )
    }
}
