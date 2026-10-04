package com.yousef.stephealth.logic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.yousef.stephealth.data.MeasurementEntity
import com.yousef.stephealth.data.PatientEntity
import com.yousef.stephealth.data.ProjectEntity
import com.yousef.stephealth.data.daysBetween
import com.yousef.stephealth.data.todayIso
import java.io.OutputStream
import java.util.Locale

/*
 * التصدير (البند 9 من المتطلبات):
 * - CSV بترميز UTF-8 مع BOM وأسماء أعمدة إنجليزية للتوافق مع Excel وSPSS.
 * - تقرير PDF عربي عبر PdfDocument الأصلية (غلاف + أرقام + جدول أسبوعي + مخطط مقارنة + جدول مرضى).
 * - ملخص نصي للمشاركة عبر Intent.
 */

object Exports {

    /* ---------------- CSV ---------------- */

    private fun esc(v: String): String =
        if (v.contains(',') || v.contains('"') || v.contains('\n') || v.contains('\r'))
            "\"" + v.replace("\"", "\"\"") + "\""
        else v

    private fun num(v: Double?): String = if (v == null) "" else String.format(Locale.US, "%.2f", v)

    private fun genderCode(g: String?): String = when (g) {
        "ذكر" -> "M"
        "أنثى" -> "F"
        else -> ""
    }

    fun buildPatientsCsv(
        project: ProjectEntity,
        patients: List<PatientEntity>,
        measurements: List<MeasurementEntity>
    ): String {
        val window = Stats.windowFor(project.startDate, project.durationDays)
        val header = "patient_id,patient_name,group,age,gender,weight_kg,height_cm,bmi," +
            "baseline_hba1c,notes,first_measurement,last_measurement," +
            "baseline_glucose,last_glucose,change_pct,missing_days,eligible,compliance_pct"
        val lines = StringBuilder(header).append('\n')
        val grouped = measurements.groupBy { it.patientId }
        patients.forEach { p ->
            val st = Stats.build(p, grouped[p.id].orEmpty(), window.first, window.second)
            val firstD = grouped[p.id]?.minByOrNull { it.date }?.date ?: ""
            val lastD = grouped[p.id]?.maxByOrNull { it.date }?.date ?: ""
            val lastReal = st.days.lastOrNull { !it.imputed }?.value
            lines.append(
                listOf(
                    p.id.toString(),
                    p.name,
                    p.groupType,
                    p.age?.toString() ?: "",
                    genderCode(p.gender),
                    num(p.weightKg),
                    num(p.heightCm),
                    num(p.weightKg?.let { w -> p.heightCm?.let { h -> bmiRaw(w, h) } }),
                    num(p.baselineHba1c),
                    p.notes ?: "",
                    firstD,
                    lastD,
                    num(st.baseline),
                    num(lastReal),
                    num(st.changePct),
                    st.missingInWindow.toString(),
                    if (st.eligible) "yes" else "no",
                    String.format(Locale.US, "%.1f", st.compliancePct)
                ).joinToString(",") { esc(it) }
            ).append('\n')
        }
        return lines.toString()
    }

    fun buildMeasurementsCsv(
        patients: List<PatientEntity>,
        measurements: List<MeasurementEntity>
    ): String {
        val header = "patient_id,patient_name,group,date,value_mgdl,entered_at"
        val lines = StringBuilder(header).append('\n')
        val byId = patients.associateBy { it.id }
        measurements.sortedWith(compareBy({ it.patientId }, { it.date })).forEach { m ->
            val p = byId[m.patientId]
            lines.append(
                listOf(
                    m.patientId.toString(),
                    p?.name ?: "",
                    p?.groupType ?: "",
                    m.date,
                    String.format(Locale.US, "%.1f", m.valueMgdl),
                    m.enteredAt
                ).joinToString(",") { esc(it) }
            ).append('\n')
        }
        return lines.toString()
    }

    private fun bmiRaw(weightKg: Double, heightCm: Double): Double {
        val m = heightCm / 100.0
        return if (m > 0) weightKg / (m * m) else 0.0
    }

    /** كتابة نص مع BOM عبر SAF */
    fun writeTextWithBom(context: Context, uri: Uri, content: String): Boolean =
        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            } != null
        } catch (t: Throwable) {
            false
        }

    /* ---------------- ملخص المشاركة ---------------- */

    fun buildShareSummary(
        project: ProjectEntity,
        patients: List<PatientEntity>,
        measurements: List<MeasurementEntity>
    ): String {
        val window = Stats.windowFor(project.startDate, project.durationDays)
        val grouped = measurements.groupBy { it.patientId }
        val stats = patients.map { Stats.build(it, grouped[it.id].orEmpty(), window.first, window.second) }
        val sCurve = Stats.dailyRelativeCurves(stats, PatientEntity.GROUP_SPORTS)
        val cCurve = Stats.dailyRelativeCurves(stats, PatientEntity.GROUP_CONTROL)
        val sLast = sCurve.lastOrNull()?.relPct
        val cLast = cCurve.lastOrNull()?.relPct
        val sb = StringBuilder()
        sb.append("مشروع: ${project.name}\n")
        sb.append("الوسم: ${project.tag}\n")
        sb.append("المدة: ${project.durationDays / 7} أسبوعًا — الحالة: ${project.status}\n")
        sb.append("المرضى: ${patients.size} (رياضية ${stats.count { it.patient.groupType == PatientEntity.GROUP_SPORTS }} / ضابطة ${stats.count { it.patient.groupType == PatientEntity.GROUP_CONTROL }})\n")
        sb.append("قياسات مسجلة: ${measurements.size}\n")
        if (sLast != null) sb.append(String.format(Locale.US, "التغير النهائي للفئة الرياضية: %+.1f%%\n", sLast))
        if (cLast != null) sb.append(String.format(Locale.US, "التغير النهائي للمجموعة الضابطة: %+.1f%%\n", cLast))
        if (sLast != null && cLast != null) {
            sb.append(String.format(Locale.US, "الفارق لصالح البرنامج الرياضي: %+.1f نقطة مئوية\n", sLast - cLast))
        }
        sb.append("— أُنشئ بواسطة تطبيق صحة رياضية (SportHealth) v2.1")
        return sb.toString()
    }

    /* ---------------- تقرير PDF ---------------- */

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val CONTENT_W = PAGE_W - MARGIN * 2

    /** تقرير PDF عربي: غلاف مصغر + أرقام + جدول أسبوعي + مخطط مقارنة + جدول مرضى */
    fun writePdfReport(
        context: Context,
        uri: Uri,
        project: ProjectEntity,
        patients: List<PatientEntity>,
        measurements: List<MeasurementEntity>
    ): Boolean {
        val doc = android.graphics.pdf.PdfDocument()
        return try {
            val window = Stats.windowFor(project.startDate, project.durationDays)
            val grouped = measurements.groupBy { it.patientId }
            val stats = patients.map { Stats.build(it, grouped[it.id].orEmpty(), window.first, window.second) }
            val sportsCurve = Stats.dailyRelativeCurves(stats, PatientEntity.GROUP_SPORTS)
            val controlCurve = Stats.dailyRelativeCurves(stats, PatientEntity.GROUP_CONTROL)
            val weekly = project.startDate?.let {
                Stats.weeklyTable(it, project.durationDays, patients, measurements)
            }

            val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 19f; color = 0xFF16305E.toInt(); typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.RIGHT
            }
            val h2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 14f; color = 0xFF16305E.toInt(); typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.RIGHT
            }
            val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = 0xFF1E293B.toInt() }
            val bodyR = Paint(body).apply { textAlign = Paint.Align.RIGHT }
            val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = 0xFF64748B.toInt() }
            val smallR = Paint(small).apply { textAlign = Paint.Align.RIGHT }
            val line = Paint().apply { color = 0xFFCBD5E1.toInt(); strokeWidth = 1f }
            val tp = TextPaint(body)

            var pageNo = 1
            var page = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            var canvas = page.canvas
            var y = MARGIN

            fun newPage() {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
                canvas = page.canvas
                y = MARGIN
            }

            fun paragraph(text: String, paint: TextPaint = tp, width: Float = CONTENT_W) {
                val sl = StaticLayout.Builder
                    .obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(40))
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setTextDirection(TextDirectionHeuristics.RTL)
                    .setLineSpacing(2f, 1.1f)
                    .setIncludePad(true)
                    .build()
                if (y + sl.height > PAGE_H - MARGIN) newPage()
                canvas.save()
                canvas.translate(PAGE_W - MARGIN - width, y)
                sl.draw(canvas)
                canvas.restore()
                y += sl.height + 6f
            }

            fun ensure(space: Float) {
                if (y + space > PAGE_H - MARGIN) newPage()
            }

            fun hr() {
                ensure(10f)
                canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, line)
                y += 10f
            }

            fun row(cells: List<Pair<String, Float>>, paint: Paint) {
                ensure(20f)
                cells.forEach { (text, xFromRight) ->
                    canvas.drawText(text, PAGE_W - MARGIN - xFromRight, y + 12f, paint)
                }
                y += 20f
            }

            /* ---- الغلاف المصغر ---- */
            y += 10f
            canvas.drawText("تقرير مشروع: ${project.name}", PAGE_W - MARGIN, y + 18f, title)
            y += 34f
            paragraph(project.tag, TextPaint(small).apply { textSize = 11f })
            hr()
            paragraph(
                "الحالة: ${project.status} — المدة: ${project.durationDays / 7} أسبوعًا (${project.durationDays} يومًا)\n" +
                    "تاريخ الإنشاء: ${project.createdAt.take(10)} — بداية القياس: ${project.startDate ?: "لم تبدأ بعد"}\n" +
                    "المرضى: ${patients.size} (رياضية ${stats.count { it.patient.groupType == PatientEntity.GROUP_SPORTS }} / ضابطة ${stats.count { it.patient.groupType == PatientEntity.GROUP_CONTROL }}) — القياسات المسجلة: ${measurements.size}\n" +
                    "تاريخ التقرير: ${todayIso()}"
            )
            hr()

            /* ---- الأرقام الرئيسية ---- */
            canvas.drawText("الأرقام الرئيسية", PAGE_W - MARGIN, y + 14f, h2)
            y += 24f
            val sLast = sportsCurve.lastOrNull()?.relPct
            val cLast = controlCurve.lastOrNull()?.relPct
            val sSummary = Stats.groupSummary(stats, "رياضية", PatientEntity.GROUP_SPORTS)
            val cSummary = Stats.groupSummary(stats, "ضابطة", PatientEntity.GROUP_CONTROL)
            paragraph(
                buildString {
                    append("متوسط التغير النهائي للفئة الرياضية: " + (sLast?.let { String.format(Locale.US, "%+.1f%%", it) } ?: "—") + "\n")
                    append("متوسط التغير النهائي للمجموعة الضابطة: " + (cLast?.let { String.format(Locale.US, "%+.1f%%", it) } ?: "—") + "\n")
                    append("متوسط السكر الحالي — رياضية: " + (sSummary.currentMeanGlucose?.let { String.format(Locale.US, "%.1f mg/dL", it) } ?: "—") +
                        " / ضابطة: " + (cSummary.currentMeanGlucose?.let { String.format(Locale.US, "%.1f mg/dL", it) } ?: "—") + "\n")
                    append("معدل الاستجابة (انخفاض ≥ 5%) — رياضية: " + String.format(Locale.US, "%.0f%%", sSummary.responseRatePct) +
                        " / ضابطة: " + String.format(Locale.US, "%.0f%%", cSummary.responseRatePct) + "\n")
                    if (sLast != null && cLast != null) {
                        append(String.format(Locale.US, "الفارق بين الفئتين: %+.1f نقطة مئوية لصالح البرنامج الرياضي\n", sLast - cLast))
                    }
                    append("مرضى مستوفون لقواعد السلامة: ${stats.count { it.eligible }} من ${stats.size}")
                }
            )
            hr()

            /* ---- الجدول الأسبوعي ---- */
            if (weekly != null) {
                canvas.drawText("الجدول الأسبوعي — المتوسط ± الانحراف المعياري (mg/dL)", PAGE_W - MARGIN, y + 14f, h2)
                y += 24f
                row(listOf("الأسبوع" to 0f, "رياضية" to 150f, "ضابطة" to 300f), h2)
                weekly.forEach { wk ->
                    val s = wk.sportsMean?.let { m -> String.format(Locale.US, "%.1f ± %.1f", m, wk.sportsSd ?: 0.0) } ?: "—"
                    val c = wk.controlMean?.let { m -> String.format(Locale.US, "%.1f ± %.1f", m, wk.controlSd ?: 0.0) } ?: "—"
                    row(listOf("أ${wk.weekNo} (${wk.fromIso.take(5)})" to 0f, s to 150f, c to 300f), bodyR)
                }
                hr()
            }

            /* ---- مخطط مقارنة الفئتين ---- */
            if (sportsCurve.isNotEmpty() || controlCurve.isNotEmpty()) {
                ensure(280f)
                canvas.drawText("مقارنة متوسط التغير النسبي اليومي بين الفئتين (%)", PAGE_W - MARGIN, y + 14f, h2)
                y += 24f
                drawComparisonChart(canvas, MARGIN, y, CONTENT_W, 210f, sportsCurve, controlCurve, small)
                y += 220f
                canvas.drawText("— رياضية", PAGE_W - MARGIN, y, smallR)
                canvas.drawText("-- ضابطة", PAGE_W - MARGIN - 70f, y, smallR)
                y += 18f
                hr()
            }

            /* ---- جدول المرضى ---- */
            canvas.drawText("جدول المرضى", PAGE_W - MARGIN, y + 14f, h2)
            y += 24f
            row(listOf("المريض" to 0f, "الفئة" to 130f, "المرجعي" to 210f, "الأخير" to 280f, "التغير%" to 355f, "فجوات" to 420f, "جاهز" to 470f), h2)
            stats.forEach { st ->
                val lastReal = st.days.lastOrNull { !it.imputed }?.value
                row(
                    listOf(
                        st.patient.name to 0f,
                        st.groupName to 130f,
                        num(st.baseline) to 210f,
                        num(lastReal) to 280f,
                        num(st.changePct) to 355f,
                        st.missingInWindow.toString() to 420f,
                        (if (st.eligible) "نعم" else "لا") to 470f
                    ),
                    bodyR
                )
            }

            doc.finishPage(page)
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    doc.writeTo(out); out.flush()
                } != null
            }.getOrDefault(false)
            ok
        } catch (t: Throwable) {
            false
        } finally {
            runCatching { doc.close() }
        }
    }

    /** مخطط المقارنة داخل PDF (android.graphics) */
    private fun drawComparisonChart(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        sports: List<GroupPoint>,
        control: List<GroupPoint>,
        labelPaint: Paint
    ) {
        val axis = Paint().apply { color = 0xFFCBD5E1.toInt(); strokeWidth = 1f }
        val zero = Paint().apply { color = 0xFF94A3B8.toInt(); strokeWidth = 1.2f }
        val sportsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF16A34A.toInt(); strokeWidth = 2f; style = Paint.Style.STROKE
        }
        val controlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF475569.toInt(); strokeWidth = 2f; style = Paint.Style.STROKE; pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 4f), 0f)
        }

        val dates = (sports.map { it.date } + control.map { it.date }).distinct().sorted()
        if (dates.isEmpty()) return
        val idx = dates.withIndex().associate { (i, d) -> d to i.toFloat() / (dates.size - 1).coerceAtLeast(1) }
        val rels = (sports.map { it.relPct } + control.map { it.relPct })
        var rMin = minOf(rels.minOrNull() ?: -1.0, -1.0)
        var rMax = maxOf(rels.maxOrNull() ?: 1.0, 1.0)
        val span = (rMax - rMin).coerceAtLeast(2.0)
        rMin -= span * 0.1; rMax += span * 0.1
        if (rMin > 0) rMin = 0.0
        if (rMax < 0) rMax = 0.0

        fun px(date: String) = x + w * (idx[date] ?: 0f)
        fun py(v: Double) = (y + h * (1.0 - (v - rMin) / (rMax - rMin))).toFloat()

        canvas.drawLine(x, py(0.0), x + w, py(0.0), zero)
        canvas.drawLine(x, y, x, y + h, axis)
        canvas.drawLine(x, y + h, x + w, y + h, axis)

        fun path(points: List<GroupPoint>, paint: Paint) {
            if (points.isEmpty()) return
            val p = android.graphics.Path()
            points.sortedBy { it.date }.forEachIndexed { i, gp ->
                val fx = px(gp.date)
                val fy = py(gp.relPct)
                if (i == 0) p.moveTo(fx, fy) else p.lineTo(fx, fy)
            }
            canvas.drawPath(p, paint)
        }
        path(sports, sportsPaint)
        path(control, controlPaint)

        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(dates.first().take(10), x, y + h + 12f, labelPaint)
        canvas.drawText(dates.last().take(10), x + w - 46f, y + h + 12f, labelPaint)
        canvas.drawText(String.format(Locale.US, "%+.0f%%", rMax), x - 2f, y + 9f, labelPaint)
        canvas.drawText(String.format(Locale.US, "%+.0f%%", rMin), x - 2f, y + h, labelPaint)
    }
}
