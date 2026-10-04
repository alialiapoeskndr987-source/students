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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yousef.stephealth.data.AppDatabase
import com.yousef.stephealth.data.PatientEntity
import com.yousef.stephealth.data.ProjectEntity
import com.yousef.stephealth.ui.theme.Navy
import com.yousef.stephealth.ui.theme.Orange
import com.yousef.stephealth.ui.theme.SlateGray
import kotlinx.coroutines.launch

/* ---------------- معالج إنشاء مشروع حقيقي (يُحفظ فعليًا في قاعدة البيانات) ---------------- */

@Composable
fun NewProjectScreen(onBack: () -> Unit, onCreated: (Long) -> Unit) {
    val context = LocalContext.current
    val db = remember(context) { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf(12) }
    var tag by remember { mutableStateOf("تأثير الجهد البدني المنظم على معالجة السكري") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        BackHeader("بدء مشروع حقيقي", onBack)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = { Text("اسم المشروع") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        ErrorText(error)
        Spacer(Modifier.height(16.dp))
        Text("مدة المشروع", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Navy)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(4, 8, 12, 16).forEach { w ->
                OutlinedButton(
                    onClick = { duration = w },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (duration == w) Orange.copy(alpha = 0.18f) else Color.Transparent,
                        contentColor = if (duration == w) Navy else SlateGray
                    )
                ) { Text("$w أسبوعًا", fontSize = 13.sp) }
            }
        }
        Text(
            "اختيارات سريعة — المدى المدعوم في المتطلبات 4 حتى 26 أسبوعًا والافتراضي 12",
            fontSize = 12.sp,
            color = SlateGray
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = tag,
            onValueChange = { tag = it },
            label = { Text("وسم المشروع (افتراضي — قابل للتعديل)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                scope.launch {
                    saving = true
                    error = null
                    try {
                        val trimmed = name.trim()
                        if (db.projectDao().countName(trimmed) > 0) {
                            error = "يوجد مشروع بهذا الاسم بالفعل — اختر اسمًا آخر"
                        } else {
                            val id = db.projectDao().insert(
                                ProjectEntity(
                                    name = trimmed,
                                    tag = tag.trim().ifEmpty { "تأثير الجهد البدني المنظم على معالجة السكري" },
                                    status = ProjectEntity.STATUS_PREPARING,
                                    mode = "real",
                                    durationDays = duration * 7,
                                    createdAt = nowStamp()
                                )
                            )
                            onCreated(id)
                        }
                    } finally {
                        saving = false
                    }
                }
            },
            enabled = name.isNotBlank() && !saving,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Navy)
        ) {
            Text(
                if (saving) "جارٍ الإنشاء..." else "إنشاء المشروع",
                fontSize = 16.sp
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/* ---------------- صفحة المشروع (الملخص والمتابعة) ---------------- */

@Composable
fun ProjectScreen(
    projectId: Long,
    onBack: () -> Unit,
    onAddPatient: () -> Unit,
    onOpenPatients: () -> Unit
) {
    val context = LocalContext.current
    val db = remember(context) { AppDatabase.get(context) }
    val project by db.projectDao().byId(projectId).collectAsState(initial = null)
    val patients by db.patientDao().byProject(projectId).collectAsState(initial = emptyList())

    val sportsCount = patients.count { it.groupType == PatientEntity.GROUP_SPORTS }
    val controlCount = patients.count { it.groupType == PatientEntity.GROUP_CONTROL }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        BackHeader("المشروع", onBack)
        Spacer(Modifier.height(12.dp))
        val p = project
        if (p == null) {
            Text("جارٍ التحميل...", fontSize = 14.sp, color = SlateGray)
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            p.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Navy,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        StatusBadge(p.status)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(p.tag, fontSize = 13.sp, color = SlateGray)
                    Spacer(Modifier.height(10.dp))
                    InfoLine("المدة", "${p.durationDays / 7} أسبوعًا (${p.durationDays} يومًا)")
                    InfoLine("تاريخ الإنشاء", p.createdAt.take(10))
                    InfoLine("المرضى", "${patients.size} — رياضية: $sportsCount / ضابطة: $controlCount")
                    if (p.status == ProjectEntity.STATUS_PREPARING) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "أضف المرضى عبر الزر أدناه لتجهيز التجربة. ستنطلق حالة «جاري» تلقائيًا مع أول قياس يومي في المرحلة 3 من التطوير.",
                            fontSize = 13.sp,
                            color = Color(0xFF9A3412)
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAddPatient,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Navy)
            ) {
                Text("إضافة مريض", fontSize = 16.sp)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onOpenPatients,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("قائمة المرضى (${patients.size})", color = Navy)
            }
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "قادم في المراحل التالية:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF9A3412)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "• المرحلة 3: التسجيل اليومي لقياسات السكر (mg/dL) + التنبيه 8 مساءً + التقويم الملون",
                        fontSize = 13.sp,
                        color = Color(0xFF475569)
                    )
                    Text(
                        "• المرحلة 4: شاشة النتائج والمخططات ومقارنة الفئتين (إثبات الفرضية)",
                        fontSize = 13.sp,
                        color = Color(0xFF475569)
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun ErrorText(message: String?) {
    if (message != null) {
        Spacer(Modifier.height(6.dp))
        Text(message, fontSize = 13.sp, color = Color(0xFFDC2626))
    }
}
