package com.yousef.stephealth.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.yousef.stephealth.data.MeasurementEntity
import com.yousef.stephealth.data.PatientEntity
import com.yousef.stephealth.data.ProjectEntity
import com.yousef.stephealth.data.nowStamp
import com.yousef.stephealth.data.todayIso
import com.yousef.stephealth.ui.theme.Navy
import com.yousef.stephealth.ui.theme.SlateGray
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/*
 * شاشة «قياسات اليوم المعلقة» (البند 7): تُفتح من الإشعار أو من صفحة المشروع،
 * تعرض المرضى الناقصين اليوم مع حقل تسجيل سريع بجانب كل واحد.
 */

@Composable
fun PendingTodayScreen(
    onBack: () -> Unit,
    onOpenPatient: (Long) -> Unit
) {
    val context = LocalContext.current
    val db = remember(context) { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()
    val today = todayIso()

    val project by db.projectDao().latest().collectAsState(initial = null)
    val patients by db.patientDao().byProject(project?.id ?: 0L).collectAsState(initial = emptyList())
    val measuredIds by db.measurementDao().measuredIdsOn(project?.id ?: 0L, today).collectAsState(initial = emptyList())

    var drafts by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var errors by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    val p = project
    val missing = patients.filter { it.id !in measuredIds.toSet() }
    val doneCount = patients.size - missing.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        BackHeader("قياسات اليوم المعلقة", onBack)
        Spacer(Modifier.height(10.dp))
        Text(today, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SlateGray)
        Spacer(Modifier.height(8.dp))

        if (p == null) {
            InfoCard(
                title = "لا يوجد مشروع بعد",
                body = "ابدأ مشروعًا حقيقيًا أولًا لتتمكن من تسجيل القياسات اليومية."
            )
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F7F5))
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "${p.name}",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Navy
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "تم تسجيل $doneCount من ${patients.size} مريضًا اليوم",
                        fontSize = 13.sp, color = SlateGray
                    )
                }
            }

            if (missing.isEmpty()) {
                Spacer(Modifier.height(14.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF16A34A)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "اكتملت قياسات اليوم — كل المرضى سجلوا قياسهم",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFF166534)
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(14.dp))
                Text(
                    "المرضى الناقصون (${missing.size})",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Navy
                )
                Spacer(Modifier.height(8.dp))
                missing.forEach { patient ->
                    QuickEntryCard(
                        patient = patient,
                        draft = drafts[patient.id] ?: "",
                        error = errors[patient.id],
                        onDraftChange = { v ->
                            drafts = drafts + (patient.id to v)
                            errors = errors - patient.id
                        },
                        onOpen = { onOpenPatient(patient.id) },
                        onSave = {
                            val value = (drafts[patient.id] ?: "").trim().replace(",", ".").toDoubleOrNull()
                            when {
                                value == null -> errors = errors + (patient.id to "أدخل قيمة رقمية")
                                value < 40.0 || value > 600.0 ->
                                    errors = errors + (patient.id to "القيمة خارج المدى (40–600)")
                                else -> scope.launch {
                                    db.measurementDao().upsert(
                                        MeasurementEntity(
                                            patientId = patient.id,
                                            date = today,
                                            valueMgdl = value,
                                            enteredAt = nowStamp()
                                        )
                                    )
                                    db.projectDao().ensureStartDate(p.id, today)
                                    val proj = db.projectDao().byId(p.id).first()
                                    if (proj != null && proj.status == ProjectEntity.STATUS_PREPARING) {
                                        db.projectDao().updateStatus(p.id, ProjectEntity.STATUS_RUNNING)
                                    }
                                    drafts = drafts - patient.id
                                    errors = errors - patient.id
                                }
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun QuickEntryCard(
    patient: PatientEntity,
    draft: String,
    error: String?,
    onDraftChange: (String) -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    patient.name,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Navy,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpen() }
                )
                GroupBadge(patient.groupType)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    label = { Text("القيمة mg/dL") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onSave) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "حفظ قياس ${patient.name}",
                        tint = Navy
                    )
                }
            }
            if (error != null) {
                Text(error, fontSize = 12.sp, color = Color(0xFFDC2626))
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF9A3412))
            Spacer(Modifier.height(4.dp))
            Text(body, fontSize = 13.sp, color = Color(0xFF475569))
        }
    }
}
