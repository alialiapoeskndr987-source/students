package com.yousef.stephealth.logic

import com.yousef.stephealth.data.MeasurementEntity
import com.yousef.stephealth.data.PatientEntity
import com.yousef.stephealth.data.addDaysIso
import com.yousef.stephealth.data.daysBetween
import com.yousef.stephealth.data.isoOf
import com.yousef.stephealth.data.parseIso
import com.yousef.stephealth.data.todayIso
import kotlin.math.abs
import kotlin.math.sqrt

/*
 * محرك التحليل — ينفذ قواعد سلامة النتائج الحرجية (البند 6 من المتطلبات) حرفيًا:
 *  1) الحد المسموح لنقص القياسات: 2 لكل مريض طوال مدة البرنامج — الثالث يرفض الفحص.
 *  2) لا يُقبل يومان متتاليان فارغان — يعاملان معاملة النقص الزائد.
 *  3) التعويض عند التحليل فقط: متوسط القياسين الحقيقيين المجاورين (استقراء خطي).
 *  4) الفجوة عند طرفي السلسلة (أول/آخر قياس في البرنامج) → توقف فوري مع سبب واضح
 *     وخيار استبعاد المريض من التحليل النهائي بعلم الطالب.
 *  5) لا يُعدَّل شيء في قاعدة البيانات — كل الحسابات في الذاكرة فقط.
 *  6) يُمنع منعًا باتًا تعويض أي قيمة فارغة بصفر أو بأي رقم افتراضي.
 */

/** قيمة يوم واحد داخل السلسلة التحليلية */
data class DayValue(
    val date: String,
    val value: Double,
    val imputed: Boolean   // true = قيمة معوضة حسابيًا (معين مفرغ في المخطط)
)

data class PatientStats(
    val patient: PatientEntity,
    val days: List<DayValue>,            // السلسلة بين أول وآخر قياس (داخلية معوضة إن لزم)
    val baseline: Double?,               // متوسط أول يومين مسجلين (المرجعي)
    val mean: Double?,                   // متوسط القيم الحقيقية
    val min: Double?,
    val max: Double?,
    val changePct: Double?,              // التغير النسبي من المرجعي إلى آخر يوم
    val missingInWindow: Int,            // الأيام الفائتة داخل نافذة البرنامج حتى اليوم السابق
    val maxConsecutiveMissing: Int,
    val missingDates: List<String>,      // عينة من تواريخ الفائت (للعرض)
    val edgeGapStart: Boolean,           // فجوة طرفية: أول قياس بعد بداية البرنامج
    val edgeGapEnd: Boolean,             // فجوة طرفية: آخر قياس قبل نهاية النافذة
    val eligible: Boolean,               // يستوفي كل قواعد السلامة؟
    val reason: String?,                 // سبب عدم الجاهزية (نص المطابقة لرسائل البند 6)
    val compliancePct: Double            // نسبة الالتزام داخل النافذة
) {
    val groupName: String
        get() = if (patient.groupType == PatientEntity.GROUP_SPORTS) "رياضية" else "ضابطة"
}

data class GroupPoint(val date: String, val relPct: Double, val n: Int)

/** توزيع الحالة اليومية لفئة: نسب من تحسن / ثابت / ارتفع */
data class DistributionPoint(
    val date: String,
    val improvedPct: Double,   // انخفاض ≥ 0.1%
    val flatPct: Double,       // ثبات ±0.1%
    val rosePct: Double        // ارتفاع ≥ 0.1%
)

data class GroupSummary(
    val label: String,
    val patientCount: Int,
    val eligibleCount: Int,
    val finalMeanChangePct: Double?,   // متوسط التغير النهائي (سالب = انخفاض)
    val currentMeanGlucose: Double?,   // متوسط آخر قيمة حقيقية لكل مريض مؤهل
    val recordedDays: Int,             // إجمالي أيام القياس الحقيقية للفئة
    val avgCompliancePct: Double,
    val responseRatePct: Double,       // نسبة من انخفض متوسطه النهائي ≥ 5%
    val best: PatientStats?,           // أكبر انخفاض
    val worst: PatientStats?,          // أكبر ارتفاع
    val dayDifference: List<GroupPoint>? // فرق الفئتين يومًا بيوم (عند «كل المرضى» فقط)
)

object Stats {

    /**
     * بناء إحصاءات مريض واحد.
     * windowStart/windowEnd تحددان نافذة عدّ الفجوات (مدة البرنامج حتى اليوم السابق
     * للمشروع الجاري، أو كامل المدة للمكتمل). مرّرهما null عندما لا توجد بداية بعد.
     */
    fun build(
        patient: PatientEntity,
        measurements: List<MeasurementEntity>,
        windowStart: String?,
        windowEnd: String?
    ): PatientStats {
        val recorded = measurements.sortedBy { it.date }
        val byDate = recorded.associate { it.date to it.valueMgdl }
        val firstDate = recorded.firstOrNull()?.date
        val lastDate = recorded.lastOrNull()?.date

        // المرجعي = متوسط أول يومين مسجلين (البند 8-2)
        val baseline = when (recorded.size) {
            0 -> null
            1 -> recorded[0].valueMgdl
            else -> (recorded[0].valueMgdl + recorded[1].valueMgdl) / 2.0
        }

        // ----- السلسلة الممتلئة بين أول وآخر قياس مع تعويض داخلي فقط -----
        val days = ArrayList<DayValue>()
        if (firstDate != null && lastDate != null) {
            var cursor = parseIso(firstDate)
            val end = parseIso(lastDate)
            var prevReal: Double? = null
            var pendingGap = ArrayList<String>()
            while (!cursor.isAfter(end)) {
                val iso = isoOf(cursor)
                val v = byDate[iso]
                if (v != null) {
                    if (pendingGap.isNotEmpty()) {
                        // القاعدة 3: متوسط المجاورين الحقيقيين قبلها وبعدها — لا صفر أبدًا
                        val filled = ((prevReal ?: v) + v) / 2.0
                        pendingGap.forEach { days.add(DayValue(it, filled, imputed = true)) }
                        pendingGap = ArrayList()
                    }
                    days.add(DayValue(iso, v, imputed = false))
                    prevReal = v
                } else {
                    pendingGap.add(iso)
                }
                cursor = cursor.plusDays(1)
            }
            // ملاحظة: آخر تكرار هو آخر قياس حقيقي بالتعريف، فلا توجد فجوة معلقة بعد الحلقة
        }

        // ----- نافذة البرنامج وعدّ الفجوات (قواعد 1 و2 و4) -----
        val wStart = windowStart ?: firstDate
        val wEnd = windowEnd ?: lastDate
        var missing = 0
        var maxRun = 0
        var run = 0
        val missingDates = ArrayList<String>()
        var edgeStart = false
        var edgeEnd = false
        var expectedDays = 0
        if (wStart != null && wEnd != null && daysBetween(wStart, wEnd) >= 0) {
            var cursor = parseIso(wStart)
            val end = parseIso(wEnd)
            while (!cursor.isAfter(end)) {
                val iso = isoOf(cursor)
                expectedDays++
                val has = byDate.containsKey(iso)
                if (!has) {
                    // القاعدة 4: فجوة قبل أول قياس أو بعد آخر قياس = فجوة طرفية (لا مجاور للتوسط)
                    if (firstDate == null || iso < firstDate) edgeStart = true
                    if (lastDate == null || iso > lastDate) edgeEnd = true
                    missing++
                    run++
                    if (run > maxRun) maxRun = run
                    if (missingDates.size < 4) missingDates.add(iso)
                } else {
                    run = 0
                }
                cursor = cursor.plusDays(1)
            }
        }

        // ----- قواعد القبول (1 و2 و4) مع رسائل المطابقة لوثيقة المتطلبات -----
        var notEligibleReason: String? = null
        when {
            edgeStart -> notEligibleReason =
                "فجوة طرفية: أول قياس بتاريخ ${firstDate ?: "—"} بعد بداية البرنامج — سجّل اليوم الفائت أو استبعد المريض من التحليل النهائي"
            edgeEnd -> notEligibleReason =
                "فجوة طرفية: آخر قياس بتاريخ ${lastDate ?: "—"} — سجّل الأيام الفائتة حتى نهاية النافذة أو استبعد المريض"
            maxRun >= 2 -> notEligibleReason =
                "يومان متتاليان بدون قياس في ${missingDates.take(2).joinToString(" و")} — يعاملان معاملة النقص الزائد"
            missing > 2 -> notEligibleReason =
                "ينقصه $missing قياسات — الحد المسموح قياسان"
        }
        val eligible = missing <= 2 && maxRun <= 1 && !edgeStart && !edgeEnd && recorded.isNotEmpty()

        val realValues = recorded.map { it.valueMgdl }
        val changePct = if (baseline != null && baseline > 0 && days.isNotEmpty()) {
            (days.last().value - baseline) / baseline * 100.0
        } else null

        return PatientStats(
            patient = patient,
            days = days,
            baseline = baseline,
            mean = if (realValues.isEmpty()) null else realValues.average(),
            min = realValues.minOrNull(),
            max = realValues.maxOrNull(),
            changePct = changePct,
            missingInWindow = missing,
            maxConsecutiveMissing = maxRun,
            missingDates = missingDates,
            edgeGapStart = edgeStart,
            edgeGapEnd = edgeEnd,
            eligible = eligible,
            reason = notEligibleReason,
            compliancePct = if (expectedDays == 0) 100.0
            else (expectedDays - missing).coerceAtLeast(0) * 100.0 / expectedDays
        )
    }

    /** انحدار خطي بسيط: يعيد (a, b) بحيث y = a + b·x */
    fun linReg(values: List<Double>): Pair<Double, Double> {
        val n = values.size
        if (n < 2) return 0.0 to (values.firstOrNull() ?: 0.0)
        val xs = (0 until n).map { it.toDouble() }
        val mx = xs.average()
        val my = values.average()
        var cov = 0.0
        var varX = 0.0
        for (i in 0 until n) {
            cov += (xs[i] - mx) * (values[i] - my)
            varX += (xs[i] - mx) * (xs[i] - mx)
        }
        val b = if (varX == 0.0) 0.0 else cov / varX
        return (my - b * mx) to b
    }

    fun sd(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val m = values.average()
        return sqrt(values.map { (it - m) * (it - m) }.average())
    }

    /**
     * منحنى متوسط التغير اليومي النسبي (البند 8-2-1) للفئة المختارة.
     * groupFilter = null تعني «كل المرضى».
     * التغير اليومي لكل مريض = (قياس اليوم − المرجعي) ÷ المرجعي × 100
     * ثم متوسطه للفئة في اليوم.
     */
    fun dailyRelativeCurves(
        stats: List<PatientStats>,
        groupFilter: String?
    ): List<GroupPoint> {
        val perDay = LinkedHashMap<String, MutableList<Double>>()
        stats.filter { it.eligible && it.baseline != null && it.baseline > 0.0 }
            .filter { groupFilter == null || it.patient.groupType == groupFilter }
            .forEach { s ->
                val base = s.baseline ?: return@forEach
                s.days.forEach { dv ->
                    perDay.getOrPut(dv.date) { ArrayList() }
                        .add((dv.value - base) / base * 100.0)
                }
            }
        return perDay.entries
            .sortedBy { it.key }
            .map { (d, vals) -> GroupPoint(d, vals.average(), vals.size) }
    }

    /** توزيع الحالة اليومية (تحسن/ثابت/ارتفاع) للفئة المختارة */
    fun dailyDistribution(
        stats: List<PatientStats>,
        groupFilter: String?
    ): List<DistributionPoint> {
        val perDay = LinkedHashMap<String, Triple<MutableList<Double>, MutableList<Double>, MutableList<Double>>>()
        stats.filter { it.eligible && it.baseline != null && it.baseline > 0.0 }
            .filter { groupFilter == null || it.patient.groupType == groupFilter }
            .forEach { s ->
                val base = s.baseline ?: return@forEach
                s.days.forEach { dv ->
                    val rel = (dv.value - base) / base * 100.0
                    val t = perDay.getOrPut(dv.date) { Triple(ArrayList(), ArrayList(), ArrayList()) }
                    when {
                        rel <= -0.1 -> t.first.add(rel)
                        rel >= 0.1 -> t.third.add(rel)
                        else -> t.second.add(rel)
                    }
                }
            }
        return perDay.entries.sortedBy { it.key }.map { (d, t) ->
            val total = (t.first.size + t.second.size + t.third.size).coerceAtLeast(1)
            DistributionPoint(
                date = d,
                improvedPct = t.first.size * 100.0 / total,
                flatPct = t.second.size * 100.0 / total,
                rosePct = t.third.size * 100.0 / total
            )
        }
    }

    /** بطاقة ملخص الفئة (البند 8-2-4) */
    fun groupSummary(
        stats: List<PatientStats>,
        label: String,
        groupFilter: String?,
        curves: Map<String, List<GroupPoint>>? = null
    ): GroupSummary {
        val chosen = stats.filter { groupFilter == null || it.patient.groupType == groupFilter }
        val eligible = chosen.filter { it.eligible && it.changePct != null }
        val finalChange = eligible.mapNotNull { it.changePct }
        val lastReal = eligible.mapNotNull { s -> s.days.lastOrNull { !it.imputed }?.value }
        val responders = eligible.count { (it.changePct ?: 0.0) <= -5.0 }

        var dayDifference: List<GroupPoint>? = null
        if (groupFilter == null && curves != null) {
            val sports = curves[PatientEntity.GROUP_SPORTS].orEmpty().associateBy { it.date }
            val control = curves[PatientEntity.GROUP_CONTROL].orEmpty().associateBy { it.date }
            dayDifference = sports.keys.sorted().filter { control.containsKey(it) }.map { d ->
                GroupPoint(d, sports[d]!!.relPct - control[d]!!.relPct, 2)
            }
        }

        return GroupSummary(
            label = label,
            patientCount = chosen.size,
            eligibleCount = eligible.size,
            finalMeanChangePct = if (finalChange.isEmpty()) null else finalChange.average(),
            currentMeanGlucose = if (lastReal.isEmpty()) null else lastReal.average(),
            recordedDays = chosen.sumOf { s -> s.days.count { !it.imputed } },
            avgCompliancePct = if (chosen.isEmpty()) 0.0 else chosen.map { it.compliancePct }.average(),
            responseRatePct = if (eligible.isEmpty()) 0.0 else responders * 100.0 / eligible.size,
            best = eligible.minByOrNull { it.changePct ?: 0.0 },
            worst = eligible.maxByOrNull { it.changePct ?: 0.0 },
            dayDifference = dayDifference
        )
    }

    /**
     * الجدول الأسبوعي: المتوسط ± الانحراف المعياري لكل مجموعة في كل أسبوع
     * (الأسبوع = 7 أيام من بداية البرنامج) على القيم الحقيقية.
     */
    data class WeekRow(
        val weekNo: Int,
        val sportsMean: Double?, val sportsSd: Double?,
        val controlMean: Double?, val controlSd: Double?,
        val fromIso: String, val toIso: String
    )

    fun weeklyTable(
        projectStart: String,
        durationDays: Int,
        patients: List<PatientEntity>,
        measurements: List<MeasurementEntity>
    ): List<WeekRow> {
        val weeks = ((durationDays + 6) / 7).coerceAtLeast(1)
        val patientById = patients.associateBy { it.id }
        val rows = ArrayList<WeekRow>(weeks)
        for (w in 0 until weeks) {
            val from = addDaysIso(projectStart, (w * 7).toLong())
            val to = addDaysIso(projectStart, (w * 7 + 6).toLong())
            val inWeek = measurements.filter { it.date in from..to }
            fun vals(group: String) = inWeek
                .filter { patientById[it.patientId]?.groupType == group }
                .map { it.valueMgdl }
            val sv = vals(PatientEntity.GROUP_SPORTS)
            val cv = vals(PatientEntity.GROUP_CONTROL)
            rows.add(
                WeekRow(
                    weekNo = w + 1,
                    sportsMean = if (sv.isEmpty()) null else sv.average(),
                    sportsSd = if (sv.size < 2) null else sd(sv),
                    controlMean = if (cv.isEmpty()) null else cv.average(),
                    controlSd = if (cv.size < 2) null else sd(cv),
                    fromIso = from, toIso = to
                )
            )
        }
        return rows
    }

    /** نافذة عدّ الفجوات لمشروع: من البداية إلى آخر يوم منقضٍ من المدة */
    fun windowFor(projectStart: String?, durationDays: Int): Pair<String?, String?> {
        val start = projectStart ?: return null to null
        val programEnd = addDaysIso(start, (durationDays - 1).toLong())
        val yesterday = addDaysIso(todayIso(), -1L)
        val end = if (parseIso(yesterday).isBefore(parseIso(programEnd))) yesterday else programEnd
        if (daysBetween(start, end) < 0) return start to null
        return start to end
    }

    /** فرق مطلق صغير للمقارنات */
    fun almostEqual(a: Double, b: Double): Boolean = abs(a - b) < 1e-9
}
