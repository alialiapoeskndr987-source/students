package com.yousef.stephealth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yousef.stephealth.data.AppDatabase
import com.yousef.stephealth.data.ProjectEntity
import com.yousef.stephealth.data.todayIso
import com.yousef.stephealth.rem.ReminderScheduler
import com.yousef.stephealth.ui.screens.AddPatientScreen
import com.yousef.stephealth.ui.screens.BackHeader
import com.yousef.stephealth.ui.screens.DemoLoader
import com.yousef.stephealth.ui.screens.DemoPickerScreen
import com.yousef.stephealth.ui.screens.DemoViewerScreen
import com.yousef.stephealth.ui.screens.NewProjectScreen
import com.yousef.stephealth.ui.screens.PatientDetailScreen
import com.yousef.stephealth.ui.screens.PatientsScreen
import com.yousef.stephealth.ui.screens.PendingTodayScreen
import com.yousef.stephealth.ui.screens.ProjectScreen
import com.yousef.stephealth.ui.screens.ResultsScreen
import com.yousef.stephealth.ui.screens.StatusBadge
import com.yousef.stephealth.ui.screens.ToolsScreen
import com.yousef.stephealth.ui.theme.Navy
import com.yousef.stephealth.ui.theme.Orange
import com.yousef.stephealth.ui.theme.SlateGray
import com.yousef.stephealth.ui.theme.StepHealthTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val openTarget = intent?.getStringExtra("open")
        setContent {
            StepHealthTheme {
                // إجبار الاتجاه من اليمين لليسار في كل الأجهزة
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    App(openPending = openTarget == "pending")
                }
            }
        }
    }
}

internal sealed interface Screen {
    data object Home : Screen
    data object NewProject : Screen
    data class Project(val projectId: Long) : Screen
    data class AddPatient(val projectId: Long) : Screen
    data class Patients(val projectId: Long) : Screen
    data class PatientDetail(val projectId: Long, val patientId: Long) : Screen
    data object PendingToday : Screen
    data class Results(val projectId: Long) : Screen
    data object Demo : Screen
    data object DemoView : Screen
    data object Tools : Screen
}

@Composable
private fun App(openPending: Boolean = false) {
    val context = LocalContext.current
    val db = remember(context) { AppDatabase.get(context) }
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    var demoDataset by remember { mutableStateOf<DemoLoader.Dataset?>(null) }
    val activeProject by db.projectDao().latest().collectAsState(initial = null)

    // إعادة جدولة التنبيه المحفوظ عند فتح التطبيق (ضمان إعادة الجدولة التلقائية)
    LaunchedEffect(Unit) {
        if (db.metaDao().get(ReminderScheduler.META_ENABLED) == "1") {
            val hour = db.metaDao().get(ReminderScheduler.META_HOUR)?.toIntOrNull()
                ?: ReminderScheduler.DEFAULT_HOUR
            ReminderScheduler.schedule(context, hour)
        }
    }
    // فتح شاشة القياسات المعلقة عند الضغط على الإشعار
    LaunchedEffect(openPending) {
        if (openPending) stack.add(Screen.PendingToday)
    }

    BackHandler(enabled = stack.size > 1) { pop(stack) }
    fun popToHome() {
        stack.clear()
        stack.add(Screen.Home)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val s = stack.last()) {
            Screen.Home -> HomeScreen(
                db = db,
                activeProject = activeProject,
                onNewProject = { stack.add(Screen.NewProject) },
                onOpenProject = { stack.add(Screen.Project(it)) },
                onDemo = { stack.add(Screen.Demo) },
                onTools = { stack.add(Screen.Tools) },
                onPending = { stack.add(Screen.PendingToday) }
            )
            Screen.NewProject -> NewProjectScreen(
                onBack = { pop(stack) },
                onCreated = { id ->
                    // استبدال شاشة الإنشاء بصفحة المشروع الجديد
                    stack.removeAt(stack.lastIndex)
                    stack.add(Screen.Project(id))
                }
            )
            is Screen.Project -> ProjectScreen(
                projectId = s.projectId,
                onBack = { pop(stack) },
                onAddPatient = { stack.add(Screen.AddPatient(s.projectId)) },
                onOpenPatients = { stack.add(Screen.Patients(s.projectId)) },
                onOpenMeasurements = { stack.add(Screen.PendingToday) },
                onOpenResults = { stack.add(Screen.Results(s.projectId)) }
            )
            is Screen.AddPatient -> AddPatientScreen(
                projectId = s.projectId,
                onBack = { pop(stack) }
            )
            is Screen.Patients -> PatientsScreen(
                projectId = s.projectId,
                onBack = { pop(stack) },
                onOpenPatient = { stack.add(Screen.PatientDetail(s.projectId, it)) }
            )
            is Screen.PatientDetail -> PatientDetailScreen(
                projectId = s.projectId,
                patientId = s.patientId,
                onBack = { pop(stack) }
            )
            Screen.PendingToday -> PendingTodayScreen(
                onBack = { pop(stack) },
                onOpenPatient = { pid ->
                    activeProject?.let { stack.add(Screen.PatientDetail(it.id, pid)) }
                }
            )
            is Screen.Results -> ResultsScreen(
                projectId = s.projectId,
                onBack = { pop(stack) }
            )
            Screen.Demo -> DemoPickerScreen(
                onBack = { pop(stack) },
                onLoaded = { dataset ->
                    demoDataset = dataset
                    stack.add(Screen.DemoView)
                }
            )
            Screen.DemoView -> {
                val dataset = demoDataset
                if (dataset == null) {
                    LaunchedEffect(Unit) { popToHome() }
                } else {
                    DemoViewerScreen(
                        dataset = dataset,
                        onExit = {
                            DemoLoader.clearContainer(context)
                            demoDataset = null
                            popToHome()
                        }
                    )
                }
            }
            Screen.Tools -> ToolsScreen(onBack = { pop(stack) })
        }
    }
}

private fun pop(stack: androidx.compose.runtime.snapshots.SnapshotStateList<Screen>) {
    if (stack.size > 1) stack.removeAt(stack.lastIndex)
}

/* ---------------- الشاشة الرئيسية ---------------- */

@Composable
private fun HomeScreen(
    db: AppDatabase,
    activeProject: ProjectEntity?,
    onNewProject: () -> Unit,
    onOpenProject: (Long) -> Unit,
    onDemo: () -> Unit,
    onTools: () -> Unit,
    onPending: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(10.dp))
        Image(
            painter = painterResource(id = R.drawable.app_banner),
            contentDescription = "رجل وامرأة يركضان في حديقة عامة",
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "صحة رياضية",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Navy
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "تأثير الجهد البدني المنظم على معالجة السكري — SportHealth v2.1",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = SlateGray
        )
        Spacer(Modifier.height(22.dp))

        if (activeProject == null) {
            NavCard(
                title = "بدء مشروع حقيقي",
                subtitle = "أنشئ تجربتك الفعلية: مجموعتان، قياس يومي للسكر، تنبيهات، ونتائج حية",
                icon = Icons.Default.PlayArrow,
                onClick = onNewProject,
                isPrimary = true
            )
        } else {
            Text(
                "مشروعك الحالي",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = SlateGray,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            ElevatedCard(
                onClick = { onOpenProject(activeProject.id) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Navy,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            activeProject.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Navy
                        )
                        Spacer(Modifier.height(6.dp))
                        StatusBadge(activeProject.status)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            activeProject.tag,
                            fontSize = 13.sp,
                            color = SlateGray,
                            maxLines = 2
                        )
                        Text(
                            "المدة: ${activeProject.durationDays / 7} أسبوعًا — اضغط للمتابعة",
                            fontSize = 13.sp,
                            color = SlateGray
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onNewProject,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("بدء مشروع جديد", color = Navy)
            }

            /* بطاقة قياسات اليوم — تسجيل سريع من الرئيسية */
            val today = todayIso()
            val patients by db.patientDao().byProject(activeProject.id)
                .collectAsState(initial = emptyList())
            val measuredToday by db.measurementDao()
                .measuredIdsOn(activeProject.id, today).collectAsState(initial = emptyList())
            if (patients.isNotEmpty()) {
                val missing = patients.size - measuredToday.size
                Spacer(Modifier.height(12.dp))
                ElevatedCard(
                    onClick = onPending,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (missing > 0) Color(0xFFFFF7ED) else Color(0xFFF0FDF4)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            tint = if (missing > 0) Color(0xFF9A3412) else Color(0xFF166534),
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (missing > 0)
                                "قياسات اليوم: ${measuredToday.size} من ${patients.size} — ${missing} ناقص، اضغط للتسجيل السريع"
                            else
                                "قياسات اليوم مكتملة — ${patients.size} من ${patients.size} — اضغط للعرض",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (missing > 0) Color(0xFF9A3412) else Color(0xFF166534)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        NavCard(
            title = "عرض نموذج تجريبي",
            subtitle = "نماذج مرفقة أو استيراد ملف .db معزول بالكامل — عرض للجنة دون المساس بمشروعك",
            icon = Icons.Default.Info,
            onClick = onDemo
        )
        Spacer(Modifier.height(12.dp))
        NavCard(
            title = "الأدوات والإعدادات",
            subtitle = "التنبيه اليومي 8 مساءً، النسخة الاحتياطية الكاملة، وعن التطبيق",
            icon = Icons.Default.Settings,
            onClick = onTools
        )
        Spacer(Modifier.height(22.dp))
        Text(
            "النسخة 2.1 (SportHealth) — تطبيق مكتمل: مشروع ومرضى، قياسات يومية، تنبيه 20:00، نتائج ومخططات وإحصاءات، وضع تجريبي معزول، وتصدير CSV/PDF",
            fontSize = 12.sp,
            color = SlateGray,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun NavCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isPrimary: Boolean = false
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isPrimary) Orange else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isPrimary) Color.White else Navy,
                modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPrimary) Color.White else Navy
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = if (isPrimary) Color.White.copy(alpha = 0.92f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
        }
    }
}
