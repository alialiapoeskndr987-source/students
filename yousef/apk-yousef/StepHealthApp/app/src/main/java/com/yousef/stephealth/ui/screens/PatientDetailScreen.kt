package com.yousef.stephealth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yousef.stephealth.data.AppDatabase
import com.yousef.stephealth.data.MeasurementEntity
import com.yousef.stephealth.data.PatientEntity
import com.yousef.stephealth.data.ProjectEntity
import com.yousef.stephealth.data.WEEKDAY_LETTERS
import com.yousef.stephealth.data.addDaysIso
import com.yousef.stephealth.data.daysBetween
import com.yousef.stephealth.data.isoOf
import com.yousef.stephealth.data.monthGridOf
import com.yousef.stephealth.data.parseIso
import com.yousef.stephealth.data.todayIso
import com.yousef.stephealth.logic.Stats
import com.yousef.stephealth.ui.theme.Navy
import com.yousef.stephealth.ui.theme.SlateGray
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/*
 * صفحة المريض (البند 5 من المتطلبات):
 * - زر «تسجيل قياس اليوم» بارز، القيمة mg/dL بمدى مقبول 40–600 وتحذير للقيم الشاذة.
 * - التاريخ يُؤخذ تلقائيًا من ساعة الهاتف — لا إدخال يدوي للتاريخ اليومي.
 * - قياس واحد لكل يوم: إعادة الإدخال تعرض «تعديل القياس الموجود».
 * - التسجيل المتأخر لأي يوم فائت عبر التقويم (يُفتح مربع الإدخال بتاريخ ذلك اليوم).
 * - تقويم شهري ملون: مسجل أخضر/أحمر حسب القيمة، فائت رمادي، مستقبلي معطل.
 * - أول قياس فعلي في المشروع يحول الحالة إلى «جاري» ويضبط تاريخ البداية.
 */

private const val MIN_GLUCOSE = 40.0
private const val MAX_GLUCOSE = 600.0
private const val OUTLIER_LOW = 70.0
private const val OUTLIER_HIGH = 300.0

@Composable
fun PatientDetailScreen(
    projectId: Long,
    patientId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember(context) { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()

    val project by db.projectDao().byId(projectId).collectAsState(initial = null)
    val patient by db.patientDao().byId(patientId).collectAsState(initial = null)
    val measurements by db.measurementDao().byPatient(patientId).collectAsState(initial = emptyList())

    var dialogDate by remember { mutableStateOf<String?>(null) }
    var editTarget by remember { mutableStateOf<MeasurementEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<MeasurementEntity?>(null) }
    var month by remember { mutableStateOf(YearMonth.now()) }

    val p = project
    val pt = patient
    val today = todayIso()
    val byDate = measurements.associateBy { it.date }
    val todayEntry = byDate[today]
    val window = Stats.windowFor(p?.startDate, p?.durationDays ?: 84)
    val stats = if (pt != null) {
        Stats.build(pt, measurements, window.first, window.second)
    } else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        BackHeader(pt?.name ?: "المريض", onBack)
        Spacer(Modifier.height(10.dp))

        if (pt == null || p == null) {
            Text("جارٍ التحميل...", fontSize = 14.sp, color = SlateGray)
        } else {
            /* بطاقة المعلومات + الجاهزية */
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(pt.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Navy)
                        GroupBadge(pt.groupType)
                    }
                    val parts = buildList {
                        pt.age?.let { add("العمر: $it") }
                        pt.gender?.let { add(it) }
                        bmiOf(pt.weightKg, pt.heightCm)?.let { add("BMI ${fmt1(round1(it))}") }
                        pt.baselineHba1c?.let { add("HbA1c ${fmt1(it)}") }
                    }
                    if (parts.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(parts.joinToString(" • "), fontSize = 13.sp, color = SlateGray)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (stats != null && measurements.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (stats.eligible) "✓ جاهز للفحص" else "✗ غير جاهز للفحص",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (stats.eligible) Color(0xFF166534) else Color(0xFFB91C1C)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "أيام فائتة: ${stats.missingInWindow} (الحد المسموح 2)",
                                fontSize = 12.sp,
                                color = SlateGray
                            )
                        }
                        if (!stats.eligible && stats.reason != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(stats.reason, fontSize = 12.sp, color = Color(0xFF9A3412))
                        }
                    } else {
                        Text(
                            "لم يُسجل أي قياس بعد — ابدأ بتسجيل قياس اليوم أدناه",
                            fontSize = 13.sp, color = SlateGray
                        )
                    }
                }
            }

            /* زر قياس اليوم (أو تعديله) */
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { dialogDate = today },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (todayEntry == null) Navy else Color(0xFF0E7490)
                )
            ) {
                Text(
                    if (todayEntry == null) "تسجيل قياس اليوم (${today})" else
                        "تعديل قياس اليوم: ${fmt1(todayEntry!!.valueMgdl)} mg/dL",
                    fontSize = 15.sp
                )
            }

            /* التقويم الشهري الملون */
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = { month = month.minusMonths(1) }) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "الشهر السابق")
                        }
                        Text(
                            "${monthGridOf(month.year, month.monthValue).monthLabel} ${month.year}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Navy
                        )
                        IconButton(onClick = { month = month.plusMonths(1) }) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "الشهر التالي")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // رأس أيام الأسبوع — يبدأ بالسبت
                    Row(Modifier.fillMaxWidth()) {
                        WEEKDAY_LETTERS.forEach { letter ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(letter, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateGray)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val grid = monthGridOf(month.year, month.monthValue)
                    var day = 1 - grid.leadingBlanks
                    while (day <= grid.daysInMonth) {
                        Row(Modifier.fillMaxWidth()) {
                            repeat(7) { col ->
                                val cellDay = day + col
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (cellDay in 1..grid.daysInMonth) {
                                        val iso = isoOf(LocalDate.of(month.year, month.monthValue, cellDay))
                                        CalendarDayCell(
                                            iso = iso,
                                            entry = byDate[iso],
                                            enabled = iso <= today && iso >= pt.createdAt.take(10),
                                            isToday = iso == today,
                                            onTap = {
                                                val existing = byDate[iso]
                                                if (existing != null) editTarget = existing
                                                else dialogDate = iso
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        day += 7
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "اضغط على يوم فائت لتسجيل قياسه المتأخر، أو على يوم مسجل لتعديله أو حذفه",
                        fontSize = 11.sp, color = SlateGray
                    )
                }
            }

            /* آخر القياسات */
            Spacer(Modifier.height(16.dp))
            if (measurements.isNotEmpty()) {
                Text("آخر القياسات", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Navy)
                Spacer(Modifier.height(8.dp))
                measurements.sortedByDescending { it.date }.take(10).forEach { m ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(m.date, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Navy)
                                Text(
                                    "سُجل: ${m.enteredAt.take(16)}",
                                    fontSize = 11.sp, color = SlateGray
                                )
                            }
                            Text(
                                "${fmt1(m.valueMgdl)} mg/dL",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (m.valueMgdl in OUTLIER_LOW..180.0) Color(0xFF166534) else Color(0xFFB91C1C)
                            )
                            IconButton(onClick = { deleteTarget = m }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "حذف قياس ${m.date}",
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    /* مربع إدخال/تعديل قيمة */
    val activeDate = dialogDate
    if (activeDate != null && pt != null) {
        MeasurementDialog(
            title = if (byDate.containsKey(activeDate)) "تعديل قياس $activeDate" else "تسجيل قياس $activeDate",
            initial = byDate[activeDate]?.valueMgdl,
            onDismiss = { dialogDate = null },
            onSave = { value ->
                dialogDate = null
                scope.launch {
                    db.measurementDao().upsert(
                        MeasurementEntity(
                            patientId = patientId,
                            date = activeDate,
                            valueMgdl = value,
                            enteredAt = nowStamp()
                        )
                    )
                    // أول قياس فعلي يضبط بداية البرنامج ويحول الحالة إلى «جاري» (البند 3)
                    db.projectDao().ensureStartDate(projectId, activeDate)
                    val proj = db.projectDao().byId(projectId).first()
                    if (proj != null && proj.status == ProjectEntity.STATUS_PREPARING) {
                        db.projectDao().updateStatus(projectId, ProjectEntity.STATUS_RUNNING)
                    }
                }
            }
        )
    }

    editTarget?.let { target ->
        MeasurementDialog(
            title = "تعديل قياس ${target.date}",
            initial = target.valueMgdl,
            onDismiss = { editTarget = null },
            onSave = { value ->
                editTarget = null
                scope.launch {
                    db.measurementDao().upsert(target.copy(valueMgdl = value, enteredAt = nowStamp()))
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف القياس؟") },
            text = { Text("سيُحذف قياس ${target.date} بقيمته ${fmt1(target.valueMgdl)} mg/dL. يمكنك تسجيله مرة أخرى لاحقًا.") },
            confirmButton = {
                TextButton(onClick = {
                    val t = target
                    deleteTarget = null
                    scope.launch { db.measurementDao().deleteById(t.id) }
                }) { Text("حذف", color = Color(0xFFDC2626)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") }
            }
        )
    }
}

/* خلية يوم في التقويم */
@Composable
private fun CalendarDayCell(
    iso: String,
    entry: MeasurementEntity?,
    enabled: Boolean,
    isToday: Boolean,
    onTap: () -> Unit
) {
    val dayNum = iso.takeLast(2).toIntOrNull() ?: 0
    val bg = when {
        entry != null && entry.valueMgdl in OUTLIER_LOW..180.0 -> Color(0xFF16A34A)  // مسجل — ضمن المقبول
        entry != null -> Color(0xFFDC2626)                                          // مسجل — خارج المقبول
        !enabled -> Color.Transparent                                               // مستقبلي/قبل الانضمام
        else -> Color(0xFFE2E8F0)                                                   // فائت
    }
    val fg = when {
        entry != null -> Color.White
        !enabled -> Color(0xFFCBD5E1)
        else -> Color(0xFF475569)
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .background(bg, CircleShape)
            .let { if (isToday) it.border(2.dp, Navy, CircleShape) else it }
            .clickable(enabled = enabled) { onTap() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$dayNum",
            fontSize = 11.sp,
            color = fg,
            textAlign = TextAlign.Center,
            fontWeight = if (entry != null || isToday) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/* مربع إدخال القيمة مع تحقق المدى وتحذير القيم الشاذة (البند 5 و11) */
@Composable
private fun MeasurementDialog(
    title: String,
    initial: Double?,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var text by remember { mutableStateOf(initial?.let { fmt1(it) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var outlierConfirmed by remember { mutableStateOf(false) }
    val parsed = text.trim().replace(",", ".").toDoubleOrNull()
    val isOutlier = parsed != null && (parsed < OUTLIER_LOW || parsed > OUTLIER_HIGH)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null; outlierConfirmed = false },
                    label = { Text("قيمة السكر (mg/dL)") },
                    supportingText = { Text("المدى المقبول: 40 حتى 600 — القيم الطبيعية للصيام 70 حتى 130") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(error!!, fontSize = 13.sp, color = Color(0xFFDC2626))
                }
                if (error == null && isOutlier) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED))
                    ) {
                        Text(
                            if (parsed!! > OUTLIER_HIGH)
                                "قيمة مرتفعة جدًا (${fmt1(parsed!!)}) — هل أنت متأكد؟ اضغط «تأكيد القيمة» لقبولها"
                            else
                                "قيمة منخفضة جدًا (${fmt1(parsed!!)}) — هل أنت متأكد؟ اضغط «تأكيد القيمة» لقبولها",
                            fontSize = 13.sp,
                            color = Color(0xFF9A3412),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                Text(
                    "يُسجل قياس واحد لكل يوم — التاريخ ووقت الإدخال يأتيان من ساعة الهاتف",
                    fontSize = 11.sp, color = SlateGray,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val v = text.trim().replace(",", ".").toDoubleOrNull()
                    when {
                        v == null -> error = "أدخل قيمة رقمية صحيحة"
                        v < MIN_GLUCOSE || v > MAX_GLUCOSE ->
                            error = "القيمة خارج المدى المقبول (40 حتى 600 mg/dL)"
                        isOutlier && !outlierConfirmed -> outlierConfirmed = true
                        else -> onSave(v)
                    }
                }
            ) {
                Text(
                    when {
                        isOutlier && !outlierConfirmed && error == null -> "تأكيد القيمة"
                        else -> "حفظ"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
