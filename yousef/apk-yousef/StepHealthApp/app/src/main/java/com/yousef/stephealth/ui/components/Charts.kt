package com.yousef.stephealth.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yousef.stephealth.logic.DistributionPoint
import com.yousef.stephealth.logic.DayValue
import com.yousef.stephealth.logic.GroupPoint
import com.yousef.stephealth.logic.Stats
import kotlin.math.abs

/*
 * مخططات مبنية على Canvas مباشرة — عربية بالكامل، بلا مكتبات خارجية،
 * واتجاه الزمن من اليسار (الأقدم) إلى اليمين (الأحدث) كالمعتاد في المخططات العلمية.
 */

internal val ChartSports = Color(0xFF16A34A)      // الفئة الرياضية
internal val ChartControl = Color(0xFF475569)     // الفئة الضابطة
internal val ChartNavy = Color(0xFF16305E)
internal val ChartOrange = Color(0xFFE8793A)
internal val ChartRed = Color(0xFFDC2626)
internal val ChartGreen = Color(0xFF16A34A)
internal val ChartGray = Color(0xFF94A3B8)
internal val ChartTrend = Color(0xFF7C3AED)
internal val ChartBand = Color(0xFF22C55E)

/** نص على الـCanvas عبر النظام الأصلي (يدعم تشكيل العربية) */
internal fun DrawScope.nativeText(
    text: String,
    x: Float,
    y: Float,
    paint: Paint,
    center: Boolean = true
) {
    paint.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

internal fun rememberPaint(color: Color, sizePx: Float = 30f): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color.toArgb()
    textSize = sizePx
}

private fun diamond(cx: Float, cy: Float, r: Float): Path = Path().apply {
    moveTo(cx, cy - r)
    lineTo(cx + r, cy)
    lineTo(cx, cy + r)
    lineTo(cx - r, cy)
    close()
}

/* ------------------- مخطط مريض خطي (البند 8-1) ------------------- */

@Composable
fun PatientLineChart(days: List<DayValue>, modifier: Modifier = Modifier) {
    val axisPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ChartGray.toArgb() } }
    Canvas(modifier.fillMaxWidth().height(250.dp)) {
        if (days.isEmpty()) return@Canvas
        val padL = 46.dp.toPx()
        val padR = 14.dp.toPx()
        val padT = 16.dp.toPx()
        val padB = 34.dp.toPx()
        val plotW = size.width - padL - padR
        val plotH = size.height - padT - padB
        if (plotW <= 0 || plotH <= 0) return@Canvas

        axisPaint.textSize = 10.sp.toPx()
        val realValues = days.filter { !it.imputed }.map { it.value }
        var vMin = minOf(realValues.minOrNull() ?: 70.0, 70.0)
        var vMax = maxOf(realValues.maxOrNull() ?: 130.0, 130.0)
        vMin -= 15.0; vMax += 15.0
        if (vMax - vMin < 40.0) vMax = vMin + 40.0

        fun xPos(i: Int): Float = if (days.size == 1) padL + plotW / 2
        else padL + plotW * i / (days.size - 1)
        fun yPos(v: Double): Float = (padT + plotH * (1.0 - (v - vMin) / (vMax - vMin))).toFloat()

        // نطاق الصيام الطبيعي المظلل 70–130 mg/dL (البند 8-1)
        drawRect(
            color = ChartBand.copy(alpha = 0.12f),
            topLeft = Offset(padL, yPos(130.0)),
            size = Size(plotW, yPos(70.0) - yPos(130.0))
        )
        drawLine(
            ChartBand.copy(alpha = 0.5f),
            Offset(padL, yPos(70.0)), Offset(padL + plotW, yPos(70.0)),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            ChartBand.copy(alpha = 0.5f),
            Offset(padL, yPos(130.0)), Offset(padL + plotW, yPos(130.0)),
            strokeWidth = 1.dp.toPx()
        )

        // شبكة وقيَم المحور العمودي
        val steps = 4
        for (k in 0..steps) {
            val v = vMin + (vMax - vMin) * k / steps
            val y = yPos(v)
            drawLine(
                Color(0xFFE2E8F0),
                Offset(padL, y), Offset(padL + plotW, y),
                strokeWidth = 1.dp.toPx()
            )
            nativeText("${v.toInt()}", padL - 6.dp.toPx(), y + 10f, axisPaint)
        }

        // خط الاتجاه (انحدار خطي بسيط)
        val (a, b) = Stats.linReg(days.map { it.value })
        if (abs(b) > 1e-9 || abs(a) > 1e-9) {
            drawLine(
                ChartTrend.copy(alpha = 0.75f),
                Offset(xPos(0), yPos(a + b * 0)),
                Offset(xPos(days.size - 1), yPos(a + b * (days.size - 1))),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
            )
        }

        // خط القيم الحقيقية
        val path = Path()
        days.forEachIndexed { i, dv ->
            if (i == 0) path.moveTo(xPos(i), yPos(dv.value)) else path.lineTo(xPos(i), yPos(dv.value))
        }
        drawPath(path, ChartNavy, style = Stroke(width = 2.5.dp.toPx()))

        // النقاط: دوائر للحقيقي ومعين مفرغ للمعوض حسابيًا (البند 8-1)
        days.forEachIndexed { i, dv ->
            val c = Offset(xPos(i), yPos(dv.value))
            if (dv.imputed) {
                drawPath(
                    diamond(c.x, c.y, 6.dp.toPx()),
                    ChartOrange, style = Stroke(width = 2.dp.toPx())
                )
            } else {
                drawCircle(ChartNavy, radius = 4.dp.toPx(), center = c)
            }
        }

        // تسميات التواريخ (أول/منتصف/آخر)
        axisPaint.textSize = 9.sp.toPx()
        nativeText(days.first().date.take(10), padL, size.height - 8f, axisPaint, center = false)
        if (days.size > 2) {
            nativeText(days[days.size / 2].date.take(10), padL + plotW / 2, size.height - 8f, axisPaint)
        }
        nativeText(days.last().date.take(10), padL + plotW, size.height - 8f, axisPaint)
    }
    Row(Modifier.padding(horizontal = 8.dp)) {
        LegendDot(ChartNavy, "قياس حقيقي")
        Spacer(Modifier.width(14.dp))
        LegendDot(ChartOrange, "قيمة معوضة حسابيًا (متوسط المجاورين)", hollow = true)
        Spacer(Modifier.width(14.dp))
        LegendDot(ChartTrend, "خط الاتجاه")
    }
}

@Composable
internal fun LegendDot(color: Color, label: String, hollow: Boolean = false) {
    Row {
        Canvas(Modifier.height(14.dp).width(14.dp).padding(top = 3.dp)) {
            if (hollow) drawPath(diamond(7.dp.toPx(), 7.dp.toPx(), 6.dp.toPx()), color, style = Stroke(1.5.dp.toPx()))
            else drawCircle(color, radius = 5.dp.toPx(), center = Offset(7.dp.toPx(), 7.dp.toPx()))
        }
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = Color(0xFF64748B))
    }
}

/* ------------------- منحنيات المجموعات (التغير النسبي) ------------------- */

@Composable
fun GroupCurvesChart(
    sports: List<GroupPoint>,
    control: List<GroupPoint>,
    modifier: Modifier = Modifier
) {
    val axisPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ChartGray.toArgb() } }
    Canvas(modifier.fillMaxWidth().height(240.dp)) {
        val all = sports + control
        if (all.isEmpty()) return@Canvas
        val dates = all.map { it.date }.distinct().sorted()
        if (dates.isEmpty()) return@Canvas
        val padL = 44.dp.toPx()
        val padR = 12.dp.toPx()
        val padT = 14.dp.toPx()
        val padB = 30.dp.toPx()
        val plotW = size.width - padL - padR
        val plotH = size.height - padT - padB
        if (plotW <= 0 || plotH <= 0) return@Canvas

        axisPaint.textSize = 10.sp.toPx()
        val byDate = dates.withIndex().associate { (i, d) -> d to i }
        val rels = all.map { it.relPct }
        var rMin = minOf(rels.min(), -1.0)
        var rMax = maxOf(rels.max(), 1.0)
        val span = maxOf(rMax - rMin, 2.0)
        rMin -= span * 0.1; rMax += span * 0.1
        if (rMin > 0) rMin = 0.0
        if (rMax < 0) rMax = 0.0

        fun xPos(date: String): Float =
            padL + plotW * (byDate[date] ?: 0) / (dates.size - 1).coerceAtLeast(1)
        fun yPos(v: Double): Float = (padT + plotH * (1.0 - (v - rMin) / (rMax - rMin))).toFloat()

        // خط الصفر
        drawLine(
            Color(0xFFCBD5E1),
            Offset(padL, yPos(0.0)), Offset(padL + plotW, yPos(0.0)),
            strokeWidth = 1.5.dp.toPx()
        )
        nativeText("0%", padL - 6.dp.toPx(), yPos(0.0) + 10f, axisPaint)

        // قيم المحور
        val steps = 4
        for (k in 0..steps) {
            val v = rMin + (rMax - rMin) * k / steps
            if (abs(v) < 0.05) continue
            val y = yPos(v)
            drawLine(
                Color(0xFFE2E8F0),
                Offset(padL, y), Offset(padL + plotW, y),
                strokeWidth = 1.dp.toPx()
            )
            nativeText(String.format(java.util.Locale.US, "%+.0f%%", v), padL - 6.dp.toPx(), y + 10f, axisPaint)
        }

        fun drawCurve(points: List<GroupPoint>, color: Color) {
            if (points.isEmpty()) return
            val p = Path()
            points.sortedBy { it.date }.forEachIndexed { i, gp ->
                val x = xPos(gp.date)
                val y = yPos(gp.relPct)
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, color, style = Stroke(width = 2.5.dp.toPx()))
            points.forEach { gp ->
                drawCircle(color, radius = 3.dp.toPx(), center = Offset(xPos(gp.date), yPos(gp.relPct)))
            }
        }
        drawCurve(sports, ChartSports)
        drawCurve(control, ChartControl)

        axisPaint.textSize = 9.sp.toPx()
        nativeText(dates.first().take(10), padL, size.height - 8f, axisPaint, center = false)
        if (dates.size > 2) nativeText(dates[dates.size / 2].take(10), padL + plotW / 2, size.height - 8f, axisPaint)
        nativeText(dates.last().take(10), padL + plotW, size.height - 8f, axisPaint)
    }
    Row(Modifier.padding(horizontal = 8.dp)) {
        LegendDot(ChartSports, "رياضية")
        Spacer(Modifier.width(14.dp))
        LegendDot(ChartControl, "ضابطة")
    }
}

/* ------------------- أعمدة التغير اليومي (أخضر/أحمر/رمادي) ------------------- */

@Composable
fun RelChangeBarChart(points: List<GroupPoint>, modifier: Modifier = Modifier) {
    val axisPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ChartGray.toArgb() } }
    Canvas(modifier.fillMaxWidth().height(220.dp)) {
        if (points.isEmpty()) return@Canvas
        val padL = 44.dp.toPx()
        val padR = 12.dp.toPx()
        val padT = 14.dp.toPx()
        val padB = 30.dp.toPx()
        val plotW = size.width - padL - padR
        val plotH = size.height - padT - padB
        if (plotW <= 0 || plotH <= 0) return@Canvas

        axisPaint.textSize = 10.sp.toPx()
        val rels = points.map { abs(it.relPct) }
        var rMax = maxOf(rels.max(), 1.0) * 1.15
        if (rMax <= 0) rMax = 1.0
        val zeroY = padT + plotH / 2
        // مقياس متماثل حول الصفر
        val scale = (plotH / 2) / rMax

        drawLine(
            Color(0xFFCBD5E1),
            Offset(padL, zeroY), Offset(padL + plotW, zeroY),
            strokeWidth = 1.5.dp.toPx()
        )
        nativeText("0%", padL - 6.dp.toPx(), zeroY + 10f, axisPaint)
        nativeText(
            String.format(java.util.Locale.US, "+%.0f%%", rMax),
            padL - 6.dp.toPx(), padT + 10f, axisPaint
        )
        nativeText(
            String.format(java.util.Locale.US, "-%.0f%%", rMax),
            padL - 6.dp.toPx(), padT + plotH + 10f, axisPaint
        )

        val n = points.size
        val slot = plotW / n
        val barW = (slot * 0.65f).coerceAtMost(28.dp.toPx())
        points.forEachIndexed { i, gp ->
            val x = padL + slot * i + (slot - barW) / 2
            val hgt = (abs(gp.relPct) * scale).toFloat().coerceAtLeast(1.5f)
            val color = when {
                gp.relPct <= -0.1 -> ChartGreen      // انخفاض
                gp.relPct >= 0.1 -> ChartRed         // ارتفاع
                else -> ChartGray                    // ثبات
            }
            if (gp.relPct <= 0) {
                drawRoundRect(
                    color,
                    topLeft = Offset(x, zeroY),
                    size = Size(barW, hgt),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
            } else {
                drawRoundRect(
                    color,
                    topLeft = Offset(x, zeroY - hgt),
                    size = Size(barW, hgt),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
            }
        }
        axisPaint.textSize = 9.sp.toPx()
        nativeText(points.first().date.take(10), padL, size.height - 8f, axisPaint, center = false)
        if (n > 2) nativeText(points[n / 2].date.take(10), padL + plotW / 2, size.height - 8f, axisPaint)
        nativeText(points.last().date.take(10), padL + plotW, size.height - 8f, axisPaint)
    }
    Row(Modifier.padding(horizontal = 8.dp)) {
        LegendDot(ChartGreen, "انخفاض")
        Spacer(Modifier.width(14.dp))
        LegendDot(ChartRed, "ارتفاع")
        Spacer(Modifier.width(14.dp))
        LegendDot(ChartGray, "ثبات ±0.1%")
    }
}

/* ------------------- توزيع الحالة اليومية (أعمدة مكدسة) ------------------- */

@Composable
fun DistributionChart(points: List<DistributionPoint>, modifier: Modifier = Modifier) {
    val axisPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ChartGray.toArgb() } }
    Canvas(modifier.fillMaxWidth().height(200.dp)) {
        if (points.isEmpty()) return@Canvas
        val padL = 34.dp.toPx()
        val padR = 12.dp.toPx()
        val padT = 12.dp.toPx()
        val padB = 28.dp.toPx()
        val plotW = size.width - padL - padR
        val plotH = size.height - padT - padB
        if (plotW <= 0 || plotH <= 0) return@Canvas

        axisPaint.textSize = 10.sp.toPx()
        val n = points.size
        val slot = plotW / n
        val barW = (slot * 0.7f).coerceAtMost(30.dp.toPx())
        points.forEachIndexed { i, dp ->
            val x = padL + slot * i + (slot - barW) / 2
            var yTop: Double = (padT + plotH).toDouble()
            val segments = listOf(
                dp.improvedPct to ChartGreen,
                dp.flatPct to ChartGray,
                dp.rosePct to ChartRed
            )
            segments.forEach { (pct, color) ->
                val h = plotH.toDouble() * pct / 100.0
                if (h > 0.5) {
                    drawRect(
                        color,
                        topLeft = Offset(x, (yTop - h).toFloat()),
                        size = Size(barW, h.toFloat())
                    )
                }
                yTop -= h
            }
        }
        axisPaint.textSize = 9.sp.toPx()
        nativeText(points.first().date.take(10), padL, size.height - 6f, axisPaint, center = false)
        if (n > 2) nativeText(points[n / 2].date.take(10), padL + plotW / 2, size.height - 6f, axisPaint)
        nativeText(points.last().date.take(10), padL + plotW, size.height - 6f, axisPaint)
    }
    Row(Modifier.padding(horizontal = 8.dp)) {
        LegendDot(ChartGreen, "تحسّن")
        Spacer(Modifier.width(14.dp))
        LegendDot(ChartGray, "ثابت")
        Spacer(Modifier.width(14.dp))
        LegendDot(ChartRed, "ارتفع")
    }
}

/* ------------------- خط زمني مصغر Sparkline ------------------- */

@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, color: Color = ChartNavy) {
    if (values.size < 2) {
        Spacer(modifier)
        return
    }
    Canvas(modifier) {
        val vMin = values.min()
        val vMax = values.max()
        val range = (vMax - vMin).coerceAtLeast(1e-6)
        val w = size.width
        val h = size.height
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = w * i / (values.size - 1)
            val y = (h - 3f) * (1f - ((v - vMin) / range).toFloat()) + 3f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
    }
}

/** عنوان قسم داخل شاشة النتائج */
@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
    )
}
