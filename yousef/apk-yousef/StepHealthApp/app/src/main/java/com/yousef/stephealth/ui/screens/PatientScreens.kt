package com.yousef.stephealth.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yousef.stephealth.data.AppDatabase
import com.yousef.stephealth.data.PatientEntity
import com.yousef.stephealth.ui.theme.Navy
import com.yousef.stephealth.ui.theme.Orange
import com.yousef.stephealth.ui.theme.OrangeDeep
import com.yousef.stephealth.ui.theme.SlateGray
import com.yousef.stephealth.ui.theme.Teal
import kotlinx.coroutines.launch

/*
 * معالج إضافة مريض متعدد الخطوات (البند 4 من وثيقة المتطلبات):
 * 1 المجموعة  2 الاسم  3 العمر  4 الجنس  5 الوزن والطول (BMI تلقائي)
 * 6 السكر التراكمي الأساسي  7 الملاحظات والملخص ثم الحفظ
 * بعد الحفظ يعود المعالج فارغًا لإضافة المريض التالي مع عدّاد حي.
 */

@Composable
fun AddPatientScreen(projectId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember(context) { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(0) }
    var groupType by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var ageText by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf<String?>(null) }
    var weightText by remember { mutableStateOf("") }
    var heightText by remember { mutableStateOf("") }
    var hba1cText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var addedCount by remember { mutableStateOf(0) }
    var lastAdded by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val groupLabel =
        if (groupType == PatientEntity.GROUP_SPORTS) "مع برنامج رياضي" else "بدون رياضة (ضابطة)"

    fun resetWizard() {
        step = 0
        groupType = null
        name = ""
        ageText = ""
        gender = null
        weightText = ""
        heightText = ""
        hba1cText = ""
        notes = ""
        error = null
    }

    fun save() {
        val trimmed = name.trim()
        scope.launch {
            saving = true
            error = null
            try {
                if (db.patientDao().countName(projectId, trimmed) > 0) {
                    error = "الاسم مستخدم مسبقًا في هذا المشروع — الأسماء يجب أن تكون فريدة"
                    step = 1
                    return@launch
                }
                db.patientDao().insert(
                    PatientEntity(
                        projectId = projectId,
                        name = trimmed,
                        age = ageText.trim().toIntOrNull(),
                        gender = gender,
                        groupType = groupType ?: PatientEntity.GROUP_CONTROL,
                        weightKg = weightText.trim().toDoubleOrNull(),
                        heightCm = heightText.trim().toDoubleOrNull(),
                        baselineHba1c = hba1cText.trim().toDoubleOrNull(),
                        notes = notes.trim().ifEmpty { null },
                        createdAt = nowStamp()
                    )
                )
                addedCount++
                lastAdded = "$trimmed (${groupLabel})"
                resetWizard()
            } finally {
                saving = false
            }
        }
    }

    fun next() {
        error = null
        when (step) {
            0 -> if (groupType == null) error = "اختر المجموعة أولًا" else step = 1
            1 -> {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) {
                    error = "اكتب اسم المريض"
                } else {
                    scope.launch {
                        if (db.patientDao().countName(projectId, trimmed) > 0) {
                            error = "الاسم مستخدم مسبقًا في هذا المشروع — الأسماء يجب أن تكون فريدة"
                        } else {
                            step = 2
                        }
                    }
                }
            }
            2 -> {
                val age = ageText.trim().toIntOrNull()
                if (age == null || age < 18 || age > 90) {
                    error = "العمر يجب أن يكون بين 18 و90 سنة"
                } else {
                    step = 3
                }
            }
            3 -> if (gender == null) error = "اختر الجنس" else step = 4
            4 -> {
                val w = weightText.trim().toDoubleOrNull()
                val h = heightText.trim().toDoubleOrNull()
                when {
                    weightText.isBlank() && heightText.isBlank() -> step = 5
                    w == null || w < 30.0 || w > 250.0 ->
                        error = "الوزن يجب أن يكون بين 30 و250 كجم (أو اتركه فارغًا)"
                    h == null || h < 120.0 || h > 230.0 ->
                        error = "الطول يجب أن يكون بين 120 و230 سم (أو اتركه فارغًا)"
                    else -> step = 5
                }
            }
            5 -> {
                val v = hba1cText.trim().toDoubleOrNull()
                when {
                    hba1cText.isBlank() -> step = 6
                    v == null || v < 3.0 || v > 20.0 ->
                        error = "قيمة HbA1c يجب أن تكون بين 3 و20 (مثال: 8.4) أو اتركها فارغة"
                    else -> step = 6
                }
            }
            6 -> save()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        BackHeader("إضافة مريض", onBack)
        Spacer(Modifier.height(8.dp))
        Text(
            "الخطوة ${step + 1} من 7",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = OrangeDeep
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = (step + 1) / 7f,
            modifier = Modifier.fillMaxWidth(),
            color = OrangeDeep,
            trackColor = Color(0xFFF1F5F9)
        )
        if (addedCount > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "✓ تم حفظ: $lastAdded — أُضيف $addedCount مريضًا في هذه الجلسة",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF166534)
            )
        }
        Spacer(Modifier.height(16.dp))

        when (step) {
            0 -> {
                Text("المجموعة", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Navy)
                Text(
                    "إلى أي مجموعة ينتمي المريض في التجربة؟",
                    fontSize = 13.sp,
                    color = SlateGray
                )
                Spacer(Modifier.height(12.dp))
                SelectableCard(
                    title = "مع برنامج رياضي",
                    subtitle = "خاضع للمراقبة — يتبع برنامج جهد بدني منظم",
                    selected = groupType == PatientEntity.GROUP_SPORTS,
                    accent = OrangeDeep,
                    onClick = { groupType = PatientEntity.GROUP_SPORTS; error = null }
                )
                Spacer(Modifier.height(10.dp))
                SelectableCard(
                    title = "بدون رياضة (مجموعة ضابطة)",
                    subtitle = "مجموعة المقارنة — دون برنامج رياضي",
                    selected = groupType == PatientEntity.GROUP_CONTROL,
                    accent = SlateGray,
                    onClick = { groupType = PatientEntity.GROUP_CONTROL; error = null }
                )
                ErrorText(error)
            }
            1 -> {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("اسم المريض") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                ErrorText(error)
            }
            2 -> {
                OutlinedTextField(
                    value = ageText,
                    onValueChange = { ageText = it.filter { ch -> ch.isDigit() }.take(3); error = null },
                    label = { Text("العمر (سنة)") },
                    supportingText = { Text("المدى المقبول: 18 حتى 90 سنة") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                ErrorText(error)
            }
            3 -> {
                Text("الجنس", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Navy)
                Spacer(Modifier.height(12.dp))
                SelectableCard(
                    title = "ذكر",
                    subtitle = "",
                    selected = gender == "ذكر",
                    accent = Navy,
                    onClick = { gender = "ذكر"; error = null }
                )
                Spacer(Modifier.height(10.dp))
                SelectableCard(
                    title = "أنثى",
                    subtitle = "",
                    selected = gender == "أنثى",
                    accent = Teal,
                    onClick = { gender = "أنثى"; error = null }
                )
                ErrorText(error)
            }
            4 -> {
                Text(
                    "الوزن والطول (اختياري)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )
                Text(
                    "يُحسب مؤشر كتلة الجسم BMI تلقائيًا عند إدخالهما",
                    fontSize = 13.sp,
                    color = SlateGray
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it; error = null },
                        label = { Text("الوزن (كجم)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { heightText = it; error = null },
                        label = { Text("الطول (سم)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                ErrorText(error)
                val bmi = bmiOf(
                    weightText.trim().toDoubleOrNull(),
                    heightText.trim().toDoubleOrNull()
                )
                if (bmi != null) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F7F5))
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "مؤشر كتلة الجسم (BMI): ${fmt1(round1(bmi))}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Navy
                            )
                            Text(
                                "التصنيف: ${bmiLabel(bmi)}",
                                fontSize = 13.sp,
                                color = SlateGray
                            )
                        }
                    }
                }
            }
            5 -> {
                OutlinedTextField(
                    value = hba1cText,
                    onValueChange = { hba1cText = it; error = null },
                    label = { Text("السكر التراكمي الأساسي HbA1c (%)") },
                    supportingText = { Text("رقم عشري مثل 8.4 — اختياري، ويفيد في ربط النتائج بالأطروحة") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                ErrorText(error)
            }
            else -> {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات صحية (أدوية، أمراض مصاحبة…) — اختياري") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(Modifier.height(14.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F7F5))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "ملخص قبل الحفظ",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Navy
                        )
                        Spacer(Modifier.height(6.dp))
                        InfoLine("المجموعة", groupLabel)
                        InfoLine("الاسم", name.trim())
                        InfoLine("العمر", "${ageText.trim()} سنة")
                        InfoLine("الجنس", gender ?: "—")
                        val w = weightText.trim().toDoubleOrNull()
                        val h = heightText.trim().toDoubleOrNull()
                        InfoLine("الوزن/الطول", if (w != null && h != null) "${fmt1(w)} كجم / ${fmt1(h)} سم" else "—")
                        val bmi = bmiOf(w, h)
                        InfoLine(
                            "BMI",
                            if (bmi != null) "${fmt1(round1(bmi))} (${bmiLabel(bmi)})" else "—"
                        )
                        InfoLine("HbA1c", if (hba1cText.isNotBlank()) "${hba1cText.trim()}%" else "—")
                        if (notes.isNotBlank()) InfoLine("ملاحظات", notes.trim())
                    }
                }
                ErrorText(error)
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) {
                OutlinedButton(
                    onClick = { step--; error = null },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) { Text("السابق", color = Navy) }
            }
            Button(
                onClick = { next() },
                enabled = !saving,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Navy)
            ) {
                Text(
                    if (step == 6) {
                        if (saving) "جارٍ الحفظ..." else "حفظ المريض"
                    } else "التالي",
                    fontSize = 15.sp
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/* ---------------- قائمة المرضى (مع حذف بتأكيد مزدوج) ---------------- */

@Composable
fun PatientsScreen(projectId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember(context) { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()
    val patients by db.patientDao().byProject(projectId).collectAsState(initial = emptyList())

    var firstConfirm by remember { mutableStateOf<PatientEntity?>(null) }
    var finalConfirm by remember { mutableStateOf<PatientEntity?>(null) }

    val sportsCount = patients.count { it.groupType == PatientEntity.GROUP_SPORTS }
    val controlCount = patients.count { it.groupType == PatientEntity.GROUP_CONTROL }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        BackHeader("قائمة المرضى", onBack)
        Spacer(Modifier.height(8.dp))
        Text(
            "العدد: ${patients.size} — رياضية: $sportsCount / ضابطة: $controlCount",
            fontSize = 14.sp,
            color = SlateGray
        )
        Spacer(Modifier.height(12.dp))
        if (patients.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F7F5))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "لا يوجد مرضى بعد",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Navy
                    )
                    Text(
                        "عد إلى شاشة المشروع واضغط «إضافة مريض» لتسجيل أول مشارك في التجربة.",
                        fontSize = 13.sp,
                        color = SlateGray
                    )
                }
            }
        }
        patients.forEach { patient ->
            PatientCard(patient = patient, onDelete = { firstConfirm = patient })
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(24.dp))
    }

    firstConfirm?.let { pd ->
        AlertDialog(
            onDismissRequest = { firstConfirm = null },
            title = { Text("حذف المريض؟") },
            text = { Text("سيُحذف «${pd.name}» مع جميع قياساته المسجلة.") },
            confirmButton = {
                TextButton(onClick = { firstConfirm = null; finalConfirm = pd }) {
                    Text("متابعة الحذف", color = Color(0xFFDC2626))
                }
            },
            dismissButton = {
                TextButton(onClick = { firstConfirm = null }) { Text("إلغاء") }
            }
        )
    }
    finalConfirm?.let { pd ->
        AlertDialog(
            onDismissRequest = { finalConfirm = null },
            title = { Text("تأكيد نهائي") },
            text = { Text("هل أنت متأكد تمامًا من حذف «${pd.name}»؟ لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = pd
                    finalConfirm = null
                    scope.launch { db.patientDao().deleteById(target.id) }
                }) { Text("حذف نهائيًا", color = Color(0xFFDC2626)) }
            },
            dismissButton = {
                TextButton(onClick = { finalConfirm = null }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun PatientCard(patient: PatientEntity, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        patient.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                    Spacer(Modifier.width(8.dp))
                    GroupBadge(patient.groupType)
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "حذف ${patient.name}",
                        tint = Color(0xFFDC2626)
                    )
                }
            }
            val parts = buildList {
                patient.age?.let { add("العمر: $it") }
                patient.gender?.let { add(it) }
                bmiOf(patient.weightKg, patient.heightCm)?.let { add("BMI ${fmt1(round1(it))}") }
                patient.baselineHba1c?.let { add("HbA1c أساسي ${fmt1(it)}") }
            }
            if (parts.isNotEmpty()) {
                Text(parts.joinToString(" • "), fontSize = 13.sp, color = SlateGray)
            }
            if (!patient.notes.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("ملاحظات: ${patient.notes}", fontSize = 12.sp, color = SlateGray)
            }
        }
    }
}
