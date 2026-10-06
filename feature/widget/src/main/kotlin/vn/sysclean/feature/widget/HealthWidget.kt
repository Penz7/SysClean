package vn.sysclean.feature.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import vn.sysclean.core.common.format.formatBytes

/**
 * Home-screen health score. Updates itself: the app pushes a redraw whenever the score,
 * storage or RAM (in whole percent) changes, and Android refreshes it every 30 minutes
 * when the app is not running.
 */
class HealthWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(NARROW, COMPACT, SQUARE, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = context.widgetEntryPoint()
        val refresher = entry.widgetRefresher()
        // Use what the app just observed when it is running, so widget and app agree exactly.
        val initial = refresher.latest.value ?: WidgetModel.from(entry.healthRepository().current())
        provideContent {
            // Collected, not captured: later updates recompose this same session.
            val model by refresher.latest.collectAsState()
            GlanceTheme { WidgetContent(model ?: initial) }
        }
    }

    companion object {
        /** Smallest a resized widget can get (minResizeWidth/Height); still gets the compact layout. */
        internal val NARROW = DpSize(110.dp, 60.dp)
        internal val COMPACT = DpSize(160.dp, 70.dp)
        internal val SQUARE = DpSize(140.dp, 140.dp)
        internal val WIDE = DpSize(250.dp, 110.dp)
    }
}

class HealthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HealthWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        context.widgetEntryPoint().widgetRefresher().start()
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.widgetEntryPoint().widgetRefresher().stop()
    }
}

@Composable
private fun WidgetContent(model: WidgetModel) {
    val context = LocalContext.current
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val size = LocalSize.current
    val compact = size.height < HealthWidget.SQUARE.height
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            // A 2x1 cell is barely 70 dp high: every dp of padding is a line of text lost.
            .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 14.dp)
            .let { if (launch != null) it.clickable(actionStartActivity(launch)) else it },
        contentAlignment = Alignment.Center,
    ) {
        when {
            compact -> CompactLayout(model, narrow = size.width < HealthWidget.COMPACT.width)
            size.width >= HealthWidget.WIDE.width -> WideLayout(model)
            else -> SquareLayout(model)
        }
    }
}

/**
 * 2x1 and similar: the score ring (its colour is the status) with storage and RAM bars
 * beside it, so all three numbers are visible at the smallest size too.
 */
@Composable
private fun CompactLayout(model: WidgetModel, narrow: Boolean) {
    val context = LocalContext.current
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Gauge(model.score, sizeDp = if (narrow) 42 else 50, scoreSp = if (narrow) 14 else 17)
        Spacer(GlanceModifier.width(if (narrow) 8.dp else 12.dp))
        Column(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            if (model.storageUsedPercent == null && model.ramUsedPercent == null) {
                StatusText(model.score, 14)
            }
            model.storageUsedPercent?.let { Meter(context.getString(R.string.widget_storage), it, barDp = 4) }
            model.ramUsedPercent?.let {
                Spacer(GlanceModifier.height(5.dp))
                Meter(context.getString(R.string.widget_ram), it, barDp = 4)
            }
        }
    }
}

/** 2x2: ring on top, status, then storage and RAM bars. */
@Composable
private fun SquareLayout(model: WidgetModel) {
    Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Gauge(model.score, sizeDp = 74, scoreSp = 24)
        StatusText(model.score, 13)
        Spacer(GlanceModifier.height(8.dp))
        model.storageUsedPercent?.let { Meter(LocalContext.current.getString(R.string.widget_storage), it) }
        model.ramUsedPercent?.let {
            Spacer(GlanceModifier.height(6.dp))
            Meter(LocalContext.current.getString(R.string.widget_ram), it)
        }
    }
}

/** 4x2: ring on the left, details on the right. */
@Composable
private fun WideLayout(model: WidgetModel) {
    val context = LocalContext.current
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Gauge(model.score, sizeDp = 96, scoreSp = 30)
        Spacer(GlanceModifier.width(16.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                context.getString(R.string.widget_title),
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
            )
            StatusText(model.score, 16)
            Spacer(GlanceModifier.height(8.dp))
            model.storageUsedPercent?.let { Meter(storageLine(model), it) }
            model.ramUsedPercent?.let {
                Spacer(GlanceModifier.height(6.dp))
                Meter(context.getString(R.string.widget_ram), it)
            }
            Spacer(GlanceModifier.height(6.dp))
            Caption(
                if (!model.scanned) context.getString(R.string.widget_scan_hint)
                else model.batteryPercent?.let { context.getString(R.string.widget_battery, it) }.orEmpty(),
            )
        }
    }
}

@Composable
private fun Gauge(score: Int?, sizeDp: Int, scoreSp: Int) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val bitmap = GaugeBitmap.render(
        score = score,
        color = scoreColor(score).toArgb(),
        trackColor = Color(0x33808080).toArgb(),
        sizePx = (sizeDp * density).toInt(),
    )
    Box(modifier = GlanceModifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        Image(ImageProvider(bitmap), contentDescription = null, modifier = GlanceModifier.fillMaxSize())
        Text(
            text = score?.toString() ?: "–",
            style = TextStyle(
                fontSize = scoreSp.sp,
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onSurface,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun StatusText(score: Int?, sizeSp: Int) {
    val id = when {
        score == null -> R.string.widget_status_unknown
        score >= 80 -> R.string.widget_status_good
        score >= 50 -> R.string.widget_status_fair
        else -> R.string.widget_status_poor
    }
    Text(
        LocalContext.current.getString(id),
        style = TextStyle(fontSize = sizeSp.sp, fontWeight = FontWeight.Medium, color = statusColor(score)),
        maxLines = 1,
    )
}

/** Label with percentage, then a thin bar coloured by how full it is. */
@Composable
private fun Meter(label: String, usedPercent: Int, barDp: Int = 5) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                label,
                style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text("$usedPercent%", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface))
        }
        Spacer(GlanceModifier.height(3.dp))
        LinearProgressIndicator(
            progress = usedPercent / 100f,
            modifier = GlanceModifier.fillMaxWidth().height(barDp.dp).cornerRadius(3.dp),
            color = meterColor(usedPercent),
            backgroundColor = ColorProvider(day = Color(0x22000000), night = Color(0x33FFFFFF)),
        )
    }
}

@Composable
private fun Caption(text: String) {
    if (text.isEmpty()) return
    Text(text, style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
}

@Composable
private fun storageLine(model: WidgetModel): String {
    val context = LocalContext.current
    val storage = context.getString(R.string.widget_storage)
    return model.freeBytes?.let { "$storage · ${context.getString(R.string.widget_storage_free, formatBytes(it))}" } ?: storage
}

// Same palette as the dashboard gauge, with lighter tones on dark home screens.
private fun scoreColor(score: Int?): Color = when {
    score == null -> Color(0xFF8A9490)
    score >= 80 -> Color(0xFF1E9E63)
    score >= 50 -> Color(0xFFE09000)
    else -> Color(0xFFD93A3A)
}

private fun statusColor(score: Int?): ColorProvider = when {
    score == null -> ColorProvider(day = Color(0xFF5F6966), night = Color(0xFFBFC9C4))
    score >= 80 -> ColorProvider(day = Color(0xFF1E8E5A), night = Color(0xFF6DD9A0))
    score >= 50 -> ColorProvider(day = Color(0xFFB26B00), night = Color(0xFFFFB95C))
    else -> ColorProvider(day = Color(0xFFBA1A1A), night = Color(0xFFFFB4AB))
}

private fun meterColor(usedPercent: Int): ColorProvider = when {
    usedPercent >= 90 -> ColorProvider(day = Color(0xFFBA1A1A), night = Color(0xFFFFB4AB))
    usedPercent >= 75 -> ColorProvider(day = Color(0xFFB26B00), night = Color(0xFFFFB95C))
    else -> ColorProvider(day = Color(0xFF006B57), night = Color(0xFF5CDBB9))
}
