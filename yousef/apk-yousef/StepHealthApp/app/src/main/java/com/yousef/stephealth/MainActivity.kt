package com.yousef.stephealth

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.yousef.stephealth.ui.screens.AddPatientScreen
import com.yousef.stephealth.ui.screens.BackHeader
import com.yousef.stephealth.ui.screens.NewProjectScreen
import com.yousef.stephealth.ui.screens.PatientsScreen
import com.yousef.stephealth.ui.screens.ProjectScreen
import com.yousef.stephealth.ui.screens.StatusBadge
import com.yousef.stephealth.ui.theme.Navy
import com.yousef.stephealth.ui.theme.Orange
import com.yousef.stephealth.ui.theme.SlateGray
import com.yousef.stephealth.ui.theme.StepHealthTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StepHealthTheme {
                // إجبار الاتجاه من اليمين لليسار في كل الأجهزة
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    App()
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
    data object Demo : Screen
    data object Tools : Screen
}

@Composable
private fun App() {
    val context = LocalContext.current
    val db = remember(context) { AppDatabase.get(context) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val activeProject by db.projectDao().latest().collectAsState(initial = null)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val s = screen) {
            Screen.Home -> HomeScreen(
                activeProject = activeProject,
                onNewProject = { screen = Screen.NewProject },
                onOpenProject = { id -> screen = Screen.Project(id) },
                onDemo = { screen = Screen.Demo },
                onTools = { screen = Screen.Tools }
            )
            Screen.NewProject -> NewProjectScreen(
                onBack = { screen = Screen.Home },
                onCreated = { id -> screen = Screen.Project(id) }
            )
            is Screen.Project -> ProjectScreen(
                projectId = s.projectId,
                onBack = { screen = Screen.Home },
                onAddPatient = { screen = Screen.AddPatient(s.projectId) },
                onOpenPatients = { screen = Screen.Patients(s.projectId) }
            )
            is Screen.AddPatient -> AddPatientScreen(
                projectId = s.projectId,
                onBack = { screen = Screen.Project(s.projectId) }
            )
            is Screen.Patients -> PatientsScreen(
                projectId = s.projectId,
                onBack = { screen = Screen.Project(s.projectId) }
            )
            Screen.Demo -> DemoScreen(onBack = { screen = Screen.Home })
            Screen.Tools -> ToolsScreen(onBack = { screen = Screen.Home })
        }
    }
}

/* ---------------- الشاشة الرئيسية ---------------- */

@Composable
private fun HomeScreen(
    activeProject: ProjectEntity?,
    onNewProject: () -> Unit,
    onOpenProject: (Long) -> Unit,
    onDemo: () -> Unit,
    onTools: () -> Unit
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
            "خطوة صحية",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Navy
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "تأثير الجهد البدني المنظم على معالجة السكري",
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
        }

        Spacer(Modifier.height(12.dp))
        NavCard(
            title = "عرض نموذج تجريبي",
            subtitle = "استورد قاعدة بيانات افتراضية واعرض النتائج أمام اللجنة دون المساس بمشروعك",
            icon = Icons.Default.Info,
            onClick = onDemo
        )
        Spacer(Modifier.height(12.dp))
        NavCard(
            title = "الأدوات والإعدادات",
            subtitle = "التنبيه اليومي، النسخ الاحتياطي، وضع العرض للجنة، والتصدير",
            icon = Icons.Default.Settings,
            onClick = onTools
        )
        Spacer(Modifier.height(22.dp))
        Text(
            "النسخة 0.2 — المرحلة الثانية: قاعدة بيانات محلية + حفظ المشروع فعليًا + معالج إضافة المرضى",
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

/* ---------------- شاشة العرض التجريبي (معاينة) ---------------- */

@Composable
private fun DemoScreen(onBack: () -> Unit) {
    PlaceholderScreen(
        title = "عرض نموذج تجريبي",
        onBack = onBack,
        items = listOf(
            "استيراد ملف قاعدة بيانات (.db) من مجلدات الهاتف أو من النماذج المرفقة",
            "ثلاثة نماذج جاهزة: 16 مشاركًا، 30 مشاركًا (مطابق للأطروحة)، و20 مشاركًا بفجوات لعرض ميزة التعويض",
            "عرض المرضى والمخططات والإحصاءات للقراءة فقط",
            "عزل كامل عن المشروع الحقيقي مع شريط «وضع تجريبي» دائم",
            "زر «إنهاء العرض التجريبي» يعيد كل شيء كما كان"
        )
    )
}

/* ---------------- شاشة الأدوات (معاينة) ---------------- */

@Composable
private fun ToolsScreen(onBack: () -> Unit) {
    PlaceholderScreen(
        title = "الأدوات والإعدادات",
        onBack = onBack,
        items = listOf(
            "التنبيه اليومي الساعة 8 مساءً بعدد القياسات الناقصة",
            "لوحة جاهزية الفحص: متابعة قواعد النقص لكل مريض",
            "تصدير CSV (متوافق مع Excel وSPSS) وتقرير PDF",
            "نسخة احتياطية كاملة للمشروع الحقيقي",
            "قفل PIN لخصوصية بيانات المرضى + وضع العرض للجنة"
        )
    )
}

@Composable
private fun PlaceholderScreen(title: String, onBack: () -> Unit, items: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        BackHeader(title, onBack)
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED))
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "قادم في المراحل التالية من التطوير:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF9A3412)
                )
                Spacer(Modifier.height(10.dp))
                items.forEach { item ->
                    Row(Modifier.padding(vertical = 5.dp)) {
                        Text(
                            "• ",
                            color = Color(0xFFC2410C),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(item, fontSize = 14.sp, color = Color(0xFF475569))
                    }
                }
            }
        }
    }
}
