package com.yousef.stephealth.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    /** المشروع النشط = آخر مشروع تم إنشاؤه */
    @Query("SELECT * FROM projects ORDER BY id DESC LIMIT 1")
    fun latest(): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun byId(id: Long): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects ORDER BY id DESC")
    fun all(): Flow<List<ProjectEntity>>

    @Query("SELECT COUNT(*) FROM projects WHERE name = :name")
    suspend fun countName(name: String): Int

    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Query("UPDATE projects SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    /** تُضبط عند أول قياس فعلي في المرحلة 3 */
    @Query("UPDATE projects SET start_date = :startDate WHERE id = :id AND start_date IS NULL")
    suspend fun ensureStartDate(id: Long, startDate: String)
}

@Dao
interface PatientDao {

    @Query("SELECT * FROM patients WHERE project_id = :projectId ORDER BY id DESC")
    fun byProject(projectId: Long): Flow<List<PatientEntity>>

    @Query("SELECT * FROM patients WHERE project_id = :projectId ORDER BY id")
    suspend fun listForProject(projectId: Long): List<PatientEntity>

    @Query("SELECT * FROM patients WHERE id = :id")
    fun byId(id: Long): Flow<PatientEntity?>

    @Query("SELECT * FROM patients WHERE id = :id")
    suspend fun byIdOnce(id: Long): PatientEntity?

    @Query("SELECT COUNT(*) FROM patients WHERE project_id = :projectId AND name = :name")
    suspend fun countName(projectId: Long, name: String): Int

    @Query("SELECT COUNT(*) FROM patients WHERE project_id = :projectId AND group_type = :groupType")
    suspend fun countByGroup(projectId: Long, groupType: String): Int

    @Query("SELECT COUNT(*) FROM patients WHERE project_id = :projectId")
    suspend fun count(projectId: Long): Int

    @Insert
    suspend fun insert(patient: PatientEntity): Long

    /** الحذف يتطلب تأكيدًا مزدوجًا في الواجهة (البند 4 من المتطلبات) */
    @Query("DELETE FROM patients WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface MeasurementDao {

    @Insert
    suspend fun insert(m: MeasurementEntity): Long

    /** قياس واحد لكل يوم لكل مريض — UNIQUE(patient_id, date) — التعديل يستبدل القيمة نفسها */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(m: MeasurementEntity): Long

    @Query("DELETE FROM measurements WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM measurements WHERE patient_id = :patientId ORDER BY date")
    fun byPatient(patientId: Long): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements WHERE patient_id = :patientId ORDER BY date")
    suspend fun listForPatient(patientId: Long): List<MeasurementEntity>

    @Query("SELECT * FROM measurements WHERE patient_id IN (SELECT id FROM patients WHERE project_id = :projectId) ORDER BY date")
    fun byProject(projectId: Long): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements WHERE patient_id IN (SELECT id FROM patients WHERE project_id = :projectId) ORDER BY date")
    suspend fun listForProject(projectId: Long): List<MeasurementEntity>

    @Query("SELECT * FROM measurements WHERE patient_id = :patientId AND date = :date")
    suspend fun findForDay(patientId: Long, date: String): MeasurementEntity?

    @Query(
        "SELECT patient_id FROM measurements WHERE date = :date AND patient_id IN " +
            "(SELECT id FROM patients WHERE project_id = :projectId)"
    )
    fun measuredIdsOn(projectId: Long, date: String): Flow<List<Long>>

    @Query(
        "SELECT patient_id FROM measurements WHERE date = :date AND patient_id IN " +
            "(SELECT id FROM patients WHERE project_id = :projectId)"
    )
    suspend fun measuredIdsOnOnce(projectId: Long, date: String): List<Long>

    @Query("SELECT COUNT(*) FROM measurements WHERE patient_id = :patientId")
    suspend fun countForPatient(patientId: Long): Int

    @Query(
        "SELECT COUNT(*) FROM measurements WHERE patient_id IN " +
            "(SELECT id FROM patients WHERE project_id = :projectId)"
    )
    suspend fun countForProject(projectId: Long): Int
}

@Dao
interface MetaDao {

    @Query("SELECT `value` FROM meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(meta: MetaEntity)
}
