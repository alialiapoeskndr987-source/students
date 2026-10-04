package com.yousef.stephealth.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * كيانات قاعدة البيانات المحلية — مطابقة حرفيًا لمخطط «Schema v1»
 * الموثق في وثيقة المتطلبات (البند 14) حتى يتوافق كل شيء لاحقًا
 * مع استيراد قواعد البيانات الافتراضية في المرحلة 5.
 */

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "tag") val tag: String,
    // قيد الإنشاء / جاري / مكتمل
    @ColumnInfo(name = "status") val status: String = STATUS_PREPARING,
    // real / demo
    @ColumnInfo(name = "mode") val mode: String = "real",
    // ISO yyyy-MM-dd — تُضبط تلقائيًا عند أول قياس فعلي (المرحلة 3)
    @ColumnInfo(name = "start_date") val startDate: String? = null,
    @ColumnInfo(name = "duration_days") val durationDays: Int,
    @ColumnInfo(name = "created_at") val createdAt: String
) {
    companion object {
        const val STATUS_PREPARING = "قيد الإنشاء"
        const val STATUS_RUNNING = "جاري"
        const val STATUS_DONE = "مكتمل"
    }
}

@Entity(
    tableName = "patients",
    indices = [Index(value = ["project_id"])],
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PatientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "project_id") val projectId: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "age") val age: Int?,
    // ذكر / أنثى
    @ColumnInfo(name = "gender") val gender: String?,
    // sports / control
    @ColumnInfo(name = "group_type") val groupType: String,
    @ColumnInfo(name = "weight_kg") val weightKg: Double?,
    @ColumnInfo(name = "height_cm") val heightCm: Double?,
    @ColumnInfo(name = "baseline_hba1c") val baselineHba1c: Double?,
    @ColumnInfo(name = "end_hba1c") val endHba1c: Double? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String
) {
    companion object {
        const val GROUP_SPORTS = "sports"
        const val GROUP_CONTROL = "control"
    }
}

@Entity(
    tableName = "measurements",
    indices = [Index(value = ["patient_id", "date"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patient_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "patient_id") val patientId: Long,
    // ISO yyyy-MM-dd
    @ColumnInfo(name = "date") val date: String,
    // mg/dL — مدى الإدخال المقبول 40 حتى 600 (يُتحقق منه في الواجهة — المرحلة 3)
    @ColumnInfo(name = "value_mgdl") val valueMgdl: Double,
    @ColumnInfo(name = "entered_at") val enteredAt: String
)

@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: String
)
