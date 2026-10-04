package com.yousef.stephealth.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yousef.stephealth.data.PatientEntity
import com.yousef.stephealth.data.ProjectEntity
import com.yousef.stephealth.ui.theme.Navy
import com.yousef.stephealth.ui.theme.SlateGray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/* عناصر واجهة مشتركة بين شاشات التطبيق */

@Composable
internal fun BackHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
        }
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Navy)
    }
}

@Composable
internal fun StatusBadge(status: String) {
    val (bg, fg) = when (status) {
        ProjectEntity.STATUS_RUNNING -> Color(0xFFF0FDF4) to Color(0xFF166534)
        ProjectEntity.STATUS_DONE -> Color(0xFFEEF2FF) to Color(0xFF3730A3)
        else -> Color(0xFFFFEDD5) to Color(0xFF9A3412)
    }
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}

@Composable
internal fun GroupBadge(groupType: String) {
    val isSports = groupType == PatientEntity.GROUP_SPORTS
    Surface(
        color = if (isSports) Color(0xFFECFDF5) else Color(0xFFF1F5F9),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = if (isSports) "رياضية" else "ضابطة",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSports) Color(0xFF166534) else Color(0xFF475569)
        )
    }
}

@Composable
internal fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Navy,
            modifier = Modifier.width(110.dp)
        )
        Text(value, fontSize = 13.sp, color = SlateGray)
    }
}

@Composable
internal fun SelectableCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) accent.copy(alpha = 0.12f) else Color.White
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) accent else Color(0xFFE2E8F0)
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Navy)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 13.sp, color = SlateGray)
        }
    }
}

/** تاريخ ووقت الإنشاء بصيغة ISO موحدة (الوقت يأتي من ساعة الهاتف) */
internal fun nowStamp(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

/** تنسيق رقم عشري بمنزلة واحدة بأرقام لاتينية (للاتساق مع SPSS لاحقًا) */
internal fun fmt1(value: Double): String = String.format(Locale.US, "%.1f", value)

/** مؤشر كتلة الجسم — يُحسب تلقائيًا من الوزن والطول */
internal fun bmiOf(weightKg: Double?, heightCm: Double?): Double? {
    if (weightKg == null || heightCm == null || heightCm <= 0.0) return null
    val m = heightCm / 100.0
    return weightKg / (m * m)
}

internal fun bmiLabel(bmi: Double): String = when {
    bmi < 18.5 -> "نقص في الوزن"
    bmi < 25.0 -> "وزن طبيعي"
    bmi < 30.0 -> "زيادة في الوزن"
    else -> "سمنة"
}

internal fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0
