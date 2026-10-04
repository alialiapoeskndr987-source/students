package com.yousef.stephealth.ui.screens

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.yousef.stephealth.data.MeasurementEntity
import com.yousef.stephealth.data.PatientEntity
import com.yousef.stephealth.data.ProjectEntity
import com.yousef.stephealth.ui.components.SectionTitle
import com.yousef.stephealth.ui.theme.Navy
import com.yousef.stephealth.ui.theme.OrangeDeep
import com.yousef.stephealth.ui.theme.SlateGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * الوضع التجريبي (البند 10) — حاوية معزولة تمامًا:
 * 1) اختيار نموذج مرفق داخل التطبيق (assets/demo) أو ملف .db من الهاتف عبر SAF.
 * 2) التحقق من بنية الجداول ثم نسخ الملف إلى مجلد عزل داخلي.
 * 3) القراءة مباشرة عبر SQLiteDatabase (كما يحدد البند 15) دون أي Room ولا كتابة.
 * 4) شريط «وضع تجريبي» دائم أعلى كل شاشات العرض.
 * 5) «إنهاء العرض التجريبي» يحذف الحاوية فورًا ويعيد التطبيق لحالته الحقيقية.
 * لا يوجد أي احتمال لخلط بيانات النموذج الافتراضي مع التجربة الحقيقية.
 */

object DemoLoader {

    data class Dataset(
        val project: ProjectEntity,
        val patients: List<PatientEntity>,
        val measurements: List<MeasurementEntity>,
        val description: String?
    )

    const val CONTAINER_DIR = "demo_container"
    const val CONTAINER_FILE = "demo.db"

    val BUNDLED = listOf(
        "demo_1_small_16p_12w.db" to
            "عينة صغيرة — 16 مشاركًا (8/8) على 12 أسبوعًا ببيانات مكتملة",
        "demo_2_thesis_30p_12w.db" to
            "عينة مطابقة للأطروحة — 30 مشاركًا (15/15) على 12 أسبوعًا",
        "demo_3_gaps_20p_8w.db" to
            "عرض ميزة التعويض — 20 مشاركًا (10/10) على 8 أسابيع بفجوات مستوفية للقواعد"
    )

    fun containerFile(context: Context): File =
        File(File(context.filesDir, CONTAINER_DIR).apply { mkdirs() }, CONTAINER_FILE)

    fun clearContainer(context: Context) {
        File(context.filesDir, CONTAINER_DIR).deleteRecursively()
    }

    fun loadFromAsset(context: Context, assetName: String): Dataset {
        val target = containerFile(context)
        context.assets.open("demo/$assetName").use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return loadFromPath(target.absolutePath)
    }

    fun loadFromUri(context: Context, uri: Uri): Dataset {
        val target = containerFile(context)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("تعذر قراءة الملف المختار")
        return loadFromPath(target.absolutePath)
    }

    private fun loadFromPath(path: String): Dataset {
        val db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val tables = HashSet<String>()
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                while (c.moveToNext()) tables.add(c.getString(0))
            }
            if (!tables.containsAll(listOf("projects", "patients", "measurements"))) {
                throw IllegalArgumentException(
                    "الملف ليس قاعدة بيانات تطبيق صالحة (جداول Schema v1 غير موجودة)"
                )
            }

            var project: ProjectEntity? = null
            var description: String? = null
            val patients = ArrayList<PatientEntity>()
            val measurements = ArrayList<MeasurementEntity>()

            db.rawQuery(
                "SELECT id, name, tag, status, mode, start_date, duration_days, created_at FROM projects ORDER BY id LIMIT 1",
                null
            ).use { c ->
                if (c.moveToFirst()) {
                    project = ProjectEntity(
                        id = c.getLong(0),
                        name = c.getString(1) ?: "مشروع تجريبي",
                        tag = c.getString(2) ?: "",
                        status = c.getString(3) ?: "مكتمل",
                        mode = c.getString(4) ?: "demo",
                        startDate = if (c.isNull(5)) null else c.getString(5),
                        durationDays = c.getInt(6),
                        createdAt = c.getString(7) ?: ""
                    )
                }
            }
            if (tables.contains("meta")) {
                db.rawQuery(
                    "SELECT value FROM meta WHERE key = 'description' LIMIT 1", null
                ).use { c -> if (c.moveToFirst()) description = c.getString(0) }
            }

            db.rawQuery(
                "SELECT id, project_id, name, age, gender, group_type, weight_kg, height_cm, " +
                    "baseline_hba1c, end_hba1c, notes, created_at FROM patients ORDER BY id",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    patients.add(
                        PatientEntity(
                            id = c.getLong(0),
                            projectId = c.getLong(1),
                            name = c.getString(2) ?: "مريض",
                            age = if (c.isNull(3)) null else c.getInt(3),
                            gender = if (c.isNull(4)) null else c.getString(4),
                            groupType = c.getString(5) ?: PatientEntity.GROUP_CONTROL,
                            weightKg = if (c.isNull(6)) null else c.getDouble(6),
                            heightCm = if (c.isNull(7)) null else c.getDouble(7),
                            baselineHba1c = if (c.isNull(8)) null else c.getDouble(8),
                            endHba1c = if (c.isNull(9)) null else c.getDouble(9),
                            notes = if (c.isNull(10)) null else c.getString(10),
                            createdAt = c.getString(11) ?: ""
                        )
                    )
                }
            }

            db.rawQuery(
                "SELECT id, patient_id, date, value_mgdl, entered_at FROM measurements ORDER BY patient_id, date",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    measurements.add(
                        MeasurementEntity(
                            id = c.getLong(0),
                            patientId = c.getLong(1),
                            date = c.getString(2) ?: "",
                            valueMgdl = c.getDouble(3),
                            enteredAt = c.getString(4) ?: ""
                        )
                    )
                }
            }

            val proj = project
                ?: throw IllegalArgumentException("القاعدة لا تحتوي على مشروع")
            return Dataset(proj, patients, measurements, description)
        } finally {
            db.close()
        }
    }
}

/* ---------------- شريط الوضع التجريبي الدائم ---------------- */

@Composable
fun DemoBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF7C2D12))
    ) {
        Text(
            "وضع تجريبي — بيانات افتراضية للعرض فقط، المشروع الحقيقي لم يُلمس",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/* ---------------- شاشة اختيار النموذج التجريبي ---------------- */

@Composable
fun DemoPickerScreen(
    onBack: () -> Unit,
    onLoaded: (DemoLoader.Dataset) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load(block: () -> DemoLoader.Dataset, label: String) {
        loading = label
        error = null
        scope.launch {
            try {
                val dataset = withContext(Dispatchers.IO) { block() }
                loading = null
                onLoaded(dataset)
            } catch (t: Throwable) {
                loading = null
                error = t.message ?: "تعذر تحميل النموذج"
            }
        }
    }

    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            load({ DemoLoader.loadFromUri(context, uri) }, "قراءة الملف المختار...")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        BackHeader("عرض نموذج تجريبي", onBack)
        Spacer(Modifier.height(12.dp))
        DemoBanner()
        Spacer(Modifier.height(12.dp))
        Text(
            "الوضع التجريبي يعمل في حاوية معزولة: يُنسخ الملف إلى مجلد داخلي خاص بالعرض، وتُعرض الشاشات للقراءة فقط. «إنهاء العرض» يحذف الحاوية ويعيد كل شيء كما كان.",
            fontSize = 12.sp, color = SlateGray
        )
        Spacer(Modifier.height(14.dp))

        Text(
            "نماذج مرفقة داخل التطبيق",
            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Navy
        )
        Spacer(Modifier.height(8.dp))
        DemoLoader.BUNDLED.forEach { (asset, desc) ->
            ElevatedCard(
                onClick = { load({ DemoLoader.loadFromAsset(context, asset) }, "تحميل $asset ...") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = OrangeDeep,
                        modifier = Modifier.width(30.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(asset, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Navy)
                        Text(desc, fontSize = 12.sp, color = SlateGray)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "أو ملف من الهاتف",
            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Navy
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { safLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Navy)
            Spacer(Modifier.width(8.dp))
            Text("اختيار ملف قاعدة بيانات (.db) من مجلدات الهاتف", color = Navy, fontSize = 13.sp)
        }

        if (loading != null) {
            Spacer(Modifier.height(12.dp))
            Text(loading!!, fontSize = 13.sp, color = OrangeDeep, fontWeight = FontWeight.Bold)
        }
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "خطأ: $error",
                    fontSize = 13.sp, color = Color(0xFFB91C1C),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/* ---------------- شاشة العرض التجريبي (للقراءة فقط) ---------------- */

@Composable
fun DemoViewerScreen(
    dataset: DemoLoader.Dataset,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        BackHeader("العرض التجريبي", onExit)
        Spacer(Modifier.height(10.dp))
        DemoBanner()
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C2D12))
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("إنهاء العرض التجريبي وحذف الحاوية")
        }
        Spacer(Modifier.height(14.dp))

        /* ملخص المشروع */
        val p = dataset.project
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        p.name,
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Navy,
                        modifier = Modifier.weight(1f)
                    )
                    StatusBadge(p.status)
                }
                Spacer(Modifier.height(6.dp))
                Text(p.tag, fontSize = 13.sp, color = SlateGray)
                dataset.description?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, fontSize = 12.sp, color = SlateGray)
                }
                Spacer(Modifier.height(8.dp))
                InfoLine("المدة", "${p.durationDays / 7} أسبوعًا (${p.durationDays} يومًا)")
                InfoLine("بداية القياس", p.startDate ?: "—")
                InfoLine(
                    "المرضى",
                    "${dataset.patients.size} — رياضية: " +
                        dataset.patients.count { it.groupType == PatientEntity.GROUP_SPORTS } +
                        " / ضابطة: " +
                        dataset.patients.count { it.groupType == PatientEntity.GROUP_CONTROL }
                )
                InfoLine("القياسات المسجلة", "${dataset.measurements.size}")
            }
        }

        Spacer(Modifier.height(12.dp))

        /* النتائج الكاملة بنفس شاشة المشروع الحقيقي — للقراءة فقط */
        ResultsContent(
            project = p,
            patients = dataset.patients,
            measurements = dataset.measurements
        )

        Spacer(Modifier.height(12.dp))
        SectionTitle("قائمة المرضى (للقراءة فقط)")
        dataset.patients.forEach { pt ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        pt.name,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Navy,
                        modifier = Modifier.weight(1f)
                    )
                    GroupBadge(pt.groupType)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        pt.baselineHba1c?.let { "HbA1c ${fmt1(it)}" } ?: "",
                        fontSize = 12.sp, color = SlateGray
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C2D12))
        ) {
            Text("إنهاء العرض التجريبي")
        }
        Spacer(Modifier.height(24.dp))
    }
}
