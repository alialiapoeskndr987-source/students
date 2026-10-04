package com.yousef.stephealth.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.ContextCompat
import com.yousef.stephealth.data.AppDatabase
import com.yousef.stephealth.data.todayIso
import com.yousef.stephealth.rem.ReminderScheduler
import com.yousef.stephealth.ui.components.SectionTitle
import com.yousef.stephealth.ui.theme.Navy
import com.yousef.stephealth.ui.theme.OrangeDeep
import com.yousef.stephealth.ui.theme.SlateGray
import kotlinx.coroutines.launch

/*
 * الأدوات والإعدادات:
 * - التنبيه اليومي (البند 7): تشغيل/تعطيل مع طلب إذن الإشعارات لأندرويد 13+،
 *   تغيير الساعة (19/20/21 — الافتراضي 20:00)، والجدولة عبر WorkManager تنجو من إعادة التشغيل.
 * - نسخة احتياطية كاملة لقاعدة البيانات الحقيقية (البند 11) عبر SAF.
 * - بطاقة «عن التطبيق».
 */

@Composable
fun ToolsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember(context) { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(false) }
    var hour by remember { mutableStateOf(ReminderScheduler.DEFAULT_HOUR) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        enabled = db.metaDao().get(ReminderScheduler.META_ENABLED) == "1"
        hour = db.metaDao().get(ReminderScheduler.META_HOUR)?.toIntOrNull()
            ?: ReminderScheduler.DEFAULT_HOUR
    }

    LaunchedEffect(toastMsg) {
        toastMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            toastMsg = null
        }
    }

    fun persist(on: Boolean, h: Int) {
        scope.launch {
            db.metaDao().put(
                com.yousef.stephealth.data.MetaEntity(ReminderScheduler.META_ENABLED, if (on) "1" else "0")
            )
            db.metaDao().put(
                com.yousef.stephealth.data.MetaEntity(ReminderScheduler.META_HOUR, h.toString())
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ReminderScheduler.schedule(context, hour)
            persist(true, hour)
            enabled = true
            toastMsg = "تم تشغيل التنبيه اليومي الساعة $hour:00"
        } else {
            toastMsg = "إذن الإشعارات مطلوب لعرض التنبيه — فعّله من إعدادات النظام"
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                // تجميع سجل WAL في الملف الرئيسي قبل النسخ لضمان اكتمال البيانات
                db.openHelper.writableDatabase
                    .query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
                context.getDatabasePath("stephealth.db").inputStream().use { input ->
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        input.copyTo(output)
                        output.flush()
                    } ?: throw IllegalStateException("تعذر فتح الملف الهدف")
                }
                true
            }.getOrDefault(false)
            toastMsg = if (ok) "تم تصدير النسخة الاحتياطية" else "فشل تصدير النسخة الاحتياطية"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        BackHeader("الأدوات والإعدادات", onBack)
        Spacer(Modifier.height(14.dp))

        /* التنبيه اليومي */
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = OrangeDeep
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "التنبيه اليومي للقياسات",
                            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Navy
                        )
                        Text(
                            "إشعار عند الساعة $hour:00 بعدد القياسات الناقصة اليوم — يعمل أثناء حالة «جاري» فقط",
                            fontSize = 12.sp, color = SlateGray
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { on ->
                            if (on) {
                                val already = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (already) {
                                    ReminderScheduler.schedule(context, hour)
                                    persist(true, hour)
                                    enabled = true
                                    toastMsg = "تم تشغيل التنبيه اليومي الساعة $hour:00"
                                } else {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                ReminderScheduler.cancel(context)
                                persist(false, hour)
                                enabled = false
                                toastMsg = "تم تعطيل التنبيه مؤقتًا — القاعدة محفوظة"
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = OrangeDeep)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "وقت التنبيه",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Navy
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(19, 20, 21).forEach { h ->
                        OutlinedButton(
                            onClick = {
                                hour = h
                                if (enabled) {
                                    ReminderScheduler.schedule(context, h)
                                    persist(true, h)
                                } else {
                                    persist(false, h)
                                }
                                toastMsg = "وقت التنبيه: $h:00"
                            },
                            colors = OutlinedButtonDefaults.outlinedButtonColors(
                                containerColor = if (hour == h) OrangeDeep.copy(alpha = 0.18f) else Color.Transparent,
                                contentColor = if (hour == h) Navy else SlateGray
                            )
                        ) { Text("$h:00", fontSize = 13.sp) }
                    }
                }
                Text(
                    "الجدولة عبر WorkManager — تنجو من إعادة تشغيل الهاتف مع إعادة جدولة تلقائية، ويمكن تعطيلها مؤقتًا دون فقدان القاعدة",
                    fontSize = 11.sp, color = SlateGray,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        /* النسخة الاحتياطية */
        SectionTitle("النسخة الاحتياطية الكاملة")
        Text(
            "تصدير قاعدة البيانات الحقيقية كملف واحد (نفس آلية الاستيراد لاحقًا) — حماية من فقدان الهاتف. احتفظ بالملف في مجلد آمن.",
            fontSize = 12.sp, color = SlateGray
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { backupLauncher.launch("sporthealth-backup-${todayIso()}.db") },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Navy)
        ) {
            Text("تصدير نسخة احتياطية من قاعدة البيانات", fontSize = 14.sp)
        }

        /* عن التطبيق */
        SectionTitle("عن التطبيق")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F7F5))
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "صحة رياضية (SportHealth) — الإصدار 2.1",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Navy
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "أداة ميدانية لطالب التخرج يوسف سليمان — كلية التربية الرياضية — لإجراء تجربة «تأثير الجهد البدني المنظم على معالجة السكري»: مجموعتان، قياس يومي للسكر mg/dL، قواعد سلامة حرجة للنتائج، مخططات وإحصاءات حية، وضع تجريبي معزول للجنة المناقشة، وتصدير CSV/PDF.",
                    fontSize = 12.sp, color = Color(0xFF475569)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "قواعد سلامة النتائج: حد النقص المسموح قياسان لكل مريض، لا يومين متتاليين فارغين، التعويض عند التحليل فقط بمتوسط القياسين المجاورين، وتوقف فوري عند أي فجوة طرفية — ولا يُعوَّض أي فارغ بصفر أبدًا.",
                    fontSize = 11.sp, color = SlateGray
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
