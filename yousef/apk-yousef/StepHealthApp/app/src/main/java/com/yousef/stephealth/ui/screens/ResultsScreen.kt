package com.yousef.stephealth.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yousef.stephealth.data.AppDatabase
import com.yousef.stephealth.data.MeasurementEntity
import com.yousef.stephealth.data.PatientEntity
import com.yousef.stephealth.data.ProjectEntity
import com.yousef.stephealth.logic.Exports
import com.yousef.stephealth.logic.GroupPoint
import com.yousef.stephealth.logic.PatientStats
import com.yousef.stephealth.logic.Stats
import com.yousef.stephealth.ui.components.DistributionChart
import com.yousef.stephealth.ui.components.GroupCurvesChart
import com.yousef.stephealth.ui.components.PatientLineChart
import com.yousef.stephealth.ui.components.RelChangeBarChart
import com.yousef.stephealth.ui.components.SectionTitle
import com.yousef.stephealth.ui.theme.Navy
import com.yousef.stephealth.ui.theme.OrangeDeep
import com.yousef.stephealth.ui.theme.SlateGray
import kotlinx.coroutines.launch
import java.util.Locale

/*
 * شاشة النتائج (البند 8) — متاحة في أي لحظة حتى قبل انتهاء البرنامج.
 * القائمة المنسدلة الأولى: مريض → مخطط خطي (مع المعوضات والنطاق الطبيعي وخط الاتجاه).
 * القائمة المنسدلة الثانية: الفئة → تُبدّل قسم الإحصاءات الجماعية فقط دون تغيير قائمة المرضى.
 * وتشمل: لوحة جاهزية الفحص (البند 6-6) + منحنى التغير النسبي + أعمدة التغير +
 * توزيع الحالة + بطاقات الملخص + مقارنة الفئتين + الجدول الأسبوعي mean±SD.
 */

@Composable
fun ResultsScreen(projectId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember(context) { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()

    val projectState by db.projectDao().byId(projectId).collectAsState(initial = null)
    val patientsState by db.patientDao().byProject(projectId).collectAsState(initial = emptyList())
    val measurementsState by db.measurementDao()
        .byProject(projectId).collectAsState(initial = emptyList())

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    val csvPatientsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val p = projectState
        if (uri != null && p != null) scope.launch {
            val csv = Exports.buildPatientsCsv(p, patientsState, measurementsState)
            toast(if (Exports.writeTextWithBom(context, uri, csv)) "تم تصدير ملف المرضى (CSV)" else "فشل التصدير")
        }
    }
    val csvMeasurementsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val p = projectState
        if (uri != null && p != null) scope.launch {
            val csv = Exports.buildMeasurementsCsv(patientsState, measurementsState)
            toast(if (Exports.writeTextWithBom(context, uri, csv)) "تم تصدير ملف القياسات (CSV)" else "فشل التصدير")
        }
    }
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val p = projectState
        if (uri != null && p != null) scope.launch {
            val ok = Exports.writePdfReport(context, uri, p, patientsState, measurementsState)
            toast(if (ok) "تم إنشاء تقرير PDF" else "تعذر إنشاء التقرير")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        BackHeader("النتائج والمخططات", onBack)
        Spacer(Modifier.height(10.dp))

        val p = projectState
        if (p == null) {
            Text("جارٍ التحميل...", fontSize = 14.sp, color = SlateGray)
        } else {
            ResultsContent(project = p, patients = patientsState, measurements = measurementsState)

            /* التصدير (البند 9) */
            SectionTitle("التصدير والمشاركة")
            Text(
                "ملفات CSV بترميز UTF-8 مع BOM وأعمدة إنجليزية تفتح في Excel وSPSS دون تشويه. اختر مجلد التنزيلات عند الحفظ.",
                fontSize = 12.sp, color = SlateGray
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { csvPatientsLauncher.launch("sporthealth-patients.csv") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Navy)
                ) { Text("CSV المرضى", fontSize = 12.sp) }
                Button(
                    onClick = { csvMeasurementsLauncher.launch("sporthealth-measurements.csv") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Navy)
                ) { Text("CSV القياسات", fontSize = 12.sp) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { pdfLauncher.launch("sporthealth-report.pdf") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeDeep)
                ) { Text("تقرير PDF", fontSize = 12.sp) }
                OutlinedButton(
                    onClick = {
                        val text = Exports.buildShareSummary(p, patientsState, measurementsState)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(send, "مشاركة الملخص"))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("مشاركة الملخص", fontSize = 12.sp, color = Navy) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/* ---------------- المحتوى المشترك (يُستخدم أيضًا في الوضع التجريبي للقراءة فقط) ---------------- */

@Composable
fun ResultsContent(
    project: ProjectEntity,
    patients: List<PatientEntity>,
    measurements: List<MeasurementEntity>
) {
    val window = Stats.windowFor(project.startDate, project.durationDays)
    val grouped = remember(measurements) { measurements.groupBy { it.patientId } }
    val stats = remember(patients, grouped, window.first, window.second) {
        patients.map { Stats.build(it, grouped[it.id].orEmpty(), window.first, window.second) }
    }

    val patientOptions = patients.map { it.name }.ifEmpty { listOf("—") }
    var patientIdx by remember(patients.size) { mutableStateOf(0) }
    var groupIdx by remember { mutableStateOf(0) }
    val groupOptions = listOf("كل المرضى", "المجموعة الرياضية", "المجموعة الضابطة")
    val groupFilter = when (groupIdx) {
        1 -> PatientEntity.GROUP_SPORTS
        2 -> PatientEntity.GROUP_CONTROL
        else -> null
    }

    val sportsCurve = remember(stats) { Stats.dailyRelativeCurves(stats, PatientEntity.GROUP_SPORTS) }
    val controlCurve = remember(stats) { Stats.dailyRelativeCurves(stats, PatientEntity.GROUP_CONTROL) }
    val allCurve = remember(stats) { Stats.dailyRelativeCurves(stats, null) }

    /* لوحة جاهزية الفحص (البند 6-6) */
    ReadinessBoard(stats)

    /* القائمة المنسدلة الأولى: مخطط المريض */
    SectionTitle("مخطط مريض — خطي يومًا بيوم")
    DropdownSelector("اختر المريض", patientOptions, patientIdx) { patientIdx = it }
    Spacer(Modifier.height(8.dp))
    val selectedStats = stats.getOrNull(patientIdx)
    if (selectedStats != null && selectedStats.days.isNotEmpty()) {
        PatientLineChart(selectedStats.days)
        Spacer(Modifier.height(10.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F7F5))
        ) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniStat("متوسط الفترة", selectedStats.mean?.let { fmt1(it) } ?: "—")
                MiniStat("أعلى قيمة", selectedStats.max?.let { fmt1(it) } ?: "—")
                MiniStat("أدنى قيمة", selectedStats.min?.let { fmt1(it) } ?: "—")
                MiniStat(
                    "نسبة التغير",
                    selectedStats.changePct?.let { String.format(Locale.US, "%+.1f%%", it) } ?: "—",
                    accent = if ((selectedStats.changePct ?: 0.0) <= 0) Color(0xFF166534) else Color(0xFFB91C1C)
                )
            }
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Text(
                "لا توجد قياسات كافية لهذا المريض بعد — سجّل القياسات أولًا من صفحة المريض",
                fontSize = 13.sp, color = SlateGray, modifier = Modifier.padding(14.dp)
            )
        }
    }

    /* القائمة المنسدلة الثانية: الفئة — تتحكم بقسم الإحصاءات فقط */
    SectionTitle("قسم الإحصاءات الجماعية حسب الفئة")
    DropdownSelector("الفئة", groupOptions, groupIdx) { groupIdx = it }
    Text(
        "تغيير الفئة لا يغير قائمة المرضى ولا مخطط المريض أعلاه — يبدّل الإحصاءات أدناه فقط",
        fontSize = 11.sp, color = SlateGray
    )
    Spacer(Modifier.height(8.dp))

    /* 1) منحنى متوسط التغير اليومي النسبي */
    Text(
        "منحنى متوسط التغير اليومي النسبي من المرجعي (متوسط أول يومين مسجلين)",
        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Navy
    )
    GroupCurvesChart(
        sports = if (groupIdx != 2) sportsCurve else emptyList(),
        control = if (groupIdx != 1) controlCurve else emptyList()
    )

    /* 2) مخطط أعمدة التغير اليومي */
    Text(
        "مخطط أعمدة متوسط التغير اليومي",
        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Navy,
        modifier = Modifier.padding(top = 10.dp)
    )
    RelChangeBarChart(
        when (groupIdx) {
            1 -> sportsCurve
            2 -> controlCurve
            else -> allCurve
        }
    )

    /* 3) توزيع الحالة اليومية */
    Text(
        "توزيع الحالة اليومية للفئة (تحسّن / ثابت / ارتفع)",
        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Navy,
        modifier = Modifier.padding(top = 10.dp)
    )
    DistributionChart(
        remember(stats, groupFilter) { Stats.dailyDistribution(stats, groupFilter) }
    )

    /* 4) بطاقات الملخص */
    val summary = remember(stats, groupFilter, sportsCurve, controlCurve) {
        Stats.groupSummary(
            stats,
            label = groupOptions[groupIdx],
            groupFilter = groupFilter,
            curves = mapOf(
                PatientEntity.GROUP_SPORTS to sportsCurve,
                PatientEntity.GROUP_CONTROL to controlCurve
            )
        )
    }
    Spacer(Modifier.height(10.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "بطاقات ملخص — ${summary.label}",
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Navy
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) {
                    StatCard(
                        "متوسط التغير النهائي",
                        summary.finalMeanChangePct?.let { String.format(Locale.US, "%+.1f%%", it) } ?: "—",
                        accent = if ((summary.finalMeanChangePct ?: 0.0) <= 0) Color(0xFF166534) else Color(0xFFB91C1C)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    StatCard(
                        "متوسط السكر الحالي",
                        summary.currentMeanGlucose?.let { "${fmt1(it)} mg/dL" } ?: "—"
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) {
                    StatCard("أيام القياس المسجلة", "${summary.recordedDays}")
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    StatCard(
                        "نسبة الالتزام",
                        String.format(Locale.US, "%.0f%%", summary.avgCompliancePct)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) {
                    StatCard(
                        "معدل الاستجابة (≤ −5%)",
                        String.format(Locale.US, "%.0f%%", summary.responseRatePct)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    StatCard(
                        "مرضى مستوفون للقواعد",
                        "${summary.eligibleCount} من ${summary.patientCount}"
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) {
                    StatCard(
                        "أفضل تحسنًا",
                        summary.best?.let { "${it.patient.name} (${String.format(Locale.US, "%+.1f%%", it.changePct ?: 0.0)})" } ?: "—",
                        accent = Color(0xFF166534)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    StatCard(
                        "أضعف تحسنًا",
                        summary.worst?.let { "${it.patient.name} (${String.format(Locale.US, "%+.1f%%", it.changePct ?: 0.0)})" } ?: "—",
                        accent = Color(0xFFB91C1C)
                    )
                }
            }
            if (groupIdx == 0) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) {
                        StatCard(
                            "الفرق بين الفئتين (رياضية − ضابطة)",
                            summary.dayDifference?.lastOrNull()
                                ?.let { String.format(Locale.US, "%+.1f نقطة", it.relPct) } ?: "—",
                            accent = OrangeDeep
                        )
                    }
                }
                Text(
                    "منحنى الفئتين معًا في مخطط التغير النسبي أعلاه هو إثبات الفرضية أمام اللجنة",
                    fontSize = 11.sp, color = SlateGray,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }

    /* 5) الجدول الأسبوعي mean±SD */
    SectionTitle("الجدول الأسبوعي — المتوسط ± الانحراف المعياري (mg/dL)")
    val weekly = project.startDate?.let {
        Stats.weeklyTable(it, project.durationDays, patients, measurements)
    }
    if (weekly == null) {
        Text(
            "يبدأ الجدول الأسبوعي بعد تسجيل أول قياس يضبط بداية البرنامج",
            fontSize = 12.sp, color = SlateGray
        )
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(
                    "الأسبوع            الفئة الرياضية            المجموعة الضابطة",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Navy
                )
                Spacer(Modifier.height(6.dp))
                weekly.forEach { wk ->
                    val s = wk.sportsMean?.let { m -> String.format(Locale.US, "%.1f ± %.1f", m, wk.sportsSd ?: 0.0) } ?: "—"
                    val c = wk.controlMean?.let { m -> String.format(Locale.US, "%.1f ± %.1f", m, wk.controlSd ?: 0.0) } ?: "—"
                    Text(
                        "أ${wk.weekNo} (${wk.fromIso.take(5)})      $s      $c",
                        fontSize = 12.sp, color = Color(0xFF334155),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/* لوحة جاهزية الفحص — لكل مريض: الفجوات و✓/✗ والسبب (البند 6-6) */
@Composable
private fun ReadinessBoard(stats: List<PatientStats>) {
    var expanded by remember { mutableStateOf(false) }
    val ready = stats.count { it.eligible }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "لوحة جاهزية الفحص",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Navy,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "✓ $ready جاهز — ✗ ${stats.size - ready} غير جاهز",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF166534)
                )
                OutlinedButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(if (expanded) "إخفاء" else "التفاصيل", fontSize = 11.sp)
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                if (stats.isEmpty()) {
                    Text("لا يوجد مرضى بعد", fontSize = 12.sp, color = SlateGray)
                }
                stats.forEach { st ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (st.eligible) "✓" else "✗",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (st.eligible) Color(0xFF166534) else Color(0xFFB91C1C)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                st.patient.name,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Navy,
                                modifier = Modifier.weight(1f)
                            )
                            GroupBadge(st.patient.groupType)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "فجوات: ${st.missingInWindow}",
                                fontSize = 12.sp, color = SlateGray
                            )
                        }
                        if (!st.eligible && st.reason != null) {
                            Text(
                                st.reason,
                                fontSize = 11.sp, color = Color(0xFF9A3412),
                                modifier = Modifier.padding(start = 20.dp, top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DropdownSelector(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "$label: ${options.getOrElse(selectedIndex) { options.first() }}",
                fontSize = 13.sp,
                color = Navy,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = "فتح القائمة")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, opt ->
                DropdownMenuItem(
                    text = { Text(opt, fontSize = 14.sp) },
                    onClick = {
                        onSelect(i)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
internal fun MiniStat(label: String, value: String, accent: Color = Navy) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = SlateGray)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent)
    }
}

@Composable
internal fun StatCard(label: String, value: String, accent: Color = Navy) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(label, fontSize = 11.sp, color = SlateGray)
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

/** نقطة منحنى مساعدة للعرض */
internal fun curveLabel(points: List<GroupPoint>): String =
    points.lastOrNull()?.let { String.format(Locale.US, "%+.1f%%", it.relPct) } ?: "—"
