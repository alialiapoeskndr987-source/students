package com.yousef.stephealth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private sealed interface Screen {
    data object Home : Screen
    data object NewProject : Screen
    data object Demo : Screen
    data object Tools : Screen
}

@Composable
private fun App() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (screen) {
            Screen.Home -> HomeScreen(
                onNewProject = { screen = Screen.NewProject },
                onDemo = { screen = Screen.Demo },
                onTools = { screen = Screen.Tools }
            )
            Screen.NewProject -> NewProjectScreen(onBack = { screen = Screen.Home })
            Screen.Demo -> DemoScreen(onBack = { screen = Screen.Home })
            Screen.Tools -> ToolsScreen(onBack = { screen = Screen.Home })
        }
    }
}

/* ---------------- الشاشة الرئيسية ---------------- */

@Composable
private fun HomeScreen(onNewProject: () -> Unit, onDemo: () -> Unit, onTools: () -> Unit) {
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
        NavCard(
            title = "بدء مشروع حقيقي",
            subtitle = "أنشئ تجربتك الفعلية: مجموعتان، قياس يومي للسكر، تنبيهات، ونتائج حية",
            icon = Icons.Default.PlayArrow,
            onClick = onNewProject,
            isPrimary = true
        )
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
            "النسخة 0.1 — المرحلة الأولى: الهيكل والتنقل ومعالج إنشاء المشروع",
            fontSize = 12.sp,
            color = SlateGray
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

/* ---------------- معالج إنشاء مشروع حقيقي ---------------- */

@Composable
private fun NewProjectScreen(onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf(12) }
    var tag by remember { mutableStateOf("تأثير الجهد البدني المنظم على معالجة السكري") }
    var created by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
            }
            Text("بدء مشروع حقيقي", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Navy)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("اسم المشروع") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
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
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = tag,
            onValueChange = { tag = it },
            label = { Text("وسم المشروع (افتراضي — قابل للتعديل)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { created = true },
            enabled = name.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Navy)
        ) {
            Text("إنشاء المشروع", fontSize = 16.sp)
        }
        if (created) {
            Spacer(Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F7F5))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Surface(
                        color = Color(0xFFFFEDD5),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "الحالة: قيد الإنشاء",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF9A3412)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Navy)
                    Spacer(Modifier.height(4.dp))
                    Text(tag, fontSize = 13.sp, color = SlateGray)
                    Text(
                        "المدة: $duration أسبوعًا (${duration * 7} يومًا)",
                        fontSize = 13.sp,
                        color = SlateGray
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "تم إنشاء المشروع في هذه المعاينة المبدئية.\n" +
                            "المرحلة التالية من التطوير ستضيف: قاعدة البيانات المحلية، " +
                            "معالج إضافة المرضى (رياضة/ضابطة)، والقياسات اليومية والتنبيهات.",
                        fontSize = 13.sp,
                        color = Color(0xFF475569)
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
            }
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Navy)
        }
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
                        Text("• ", color = OrangeDeepText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(item, fontSize = 14.sp, color = Color(0xFF475569))
                    }
                }
            }
        }
    }
}

private val OrangeDeepText = Color(0xFFC2410C)
