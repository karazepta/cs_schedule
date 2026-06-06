package ru.vsu.csschedule.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

enum class WeekType {
    NUMERATOR,
    DENOMINATOR,
}

enum class ScheduleOwnerType {
    GROUP,
    TEACHER,
    AUDITORIUM,
}

enum class ImportStatus {
    IDLE,
    REFRESHING,
    UNCHANGED,
    DOWNLOADED,
    MAPPED,
    FAILED,
}

class AppTypeConverters {
    @TypeConverter
    fun toWeekType(value: String): WeekType = WeekType.valueOf(value)

    @TypeConverter
    fun fromWeekType(value: WeekType): String = value.name

    @TypeConverter
    fun toScheduleOwnerType(value: String): ScheduleOwnerType = ScheduleOwnerType.valueOf(value)

    @TypeConverter
    fun fromScheduleOwnerType(value: ScheduleOwnerType): String = value.name

    @TypeConverter
    fun toImportStatus(value: String): ImportStatus = ImportStatus.valueOf(value)

    @TypeConverter
    fun fromImportStatus(value: ImportStatus): String = value.name
}

@Entity(tableName = "semesters")
data class SemesterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "week_days")
data class WeekDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val orderIndex: Int,
)

@Entity(tableName = "pair_times")
data class PairTimeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val orderIndex: Int,
)

@Entity(tableName = "full_pair_times")
data class FullPairTimeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val semesterId: Long,
    val weekType: WeekType,
    val weekDayId: Long,
    val pairTimeId: Long,
)

@Entity(tableName = "teachers")
data class TeacherEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "auditoriums")
data class AuditoriumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "study_groups")
data class StudyGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val courseNumber: Int? = null,
)

@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fullPairTimeId: Long,
    val subjectName: String,
    val auditoriumId: Long?,
    val groupId: Long,
    val teacherId: Long?,
)

@Entity(
    tableName = "saved_schedules",
    indices = [Index(value = ["type", "referenceId"], unique = true)],
)
data class SavedScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ScheduleOwnerType,
    val referenceId: Long,
    val displayName: String,
    val createdAt: Long,
)

@Entity(tableName = "import_snapshots")
data class ImportSnapshotEntity(
    @PrimaryKey val key: String = ACTIVE_KEY,
    val sourcePageUrl: String,
    val sheetUrl: String? = null,
    val rawFilePath: String? = null,
    val rawHash: String? = null,
    val status: ImportStatus = ImportStatus.IDLE,
    val message: String? = null,
    val lastCheckedAt: Long? = null,
    val lastImportedAt: Long? = null,
) {
    companion object {
        const val ACTIVE_KEY = "active"
    }
}

data class NamedEntityRow(
    val id: Long,
    val name: String,
)

data class OrderedNameRow(
    val name: String,
    val orderIndex: Int,
)

data class LessonRow(
    val id: Long,
    val semesterName: String,
    val weekDayName: String,
    val weekType: WeekType,
    val timeRange: String,
    val subjectName: String,
    val teacherName: String?,
    val groupName: String,
    val auditoriumName: String?,
)

@Dao
interface ScheduleDao {
    @Query(
        """
        SELECT * FROM saved_schedules
        WHERE displayName LIKE '%' || :query || '%'
        ORDER BY createdAt DESC, displayName ASC
        """
    )
    fun observeSavedSchedules(query: String): Flow<List<SavedScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSavedSchedule(entity: SavedScheduleEntity): Long

    @Query("DELETE FROM saved_schedules WHERE id = :id")
    suspend fun deleteSavedSchedule(id: Long)

    @Query("SELECT * FROM import_snapshots WHERE `key` = :key LIMIT 1")
    fun observeImportSnapshot(key: String = ImportSnapshotEntity.ACTIVE_KEY): Flow<ImportSnapshotEntity?>

    @Query("SELECT * FROM import_snapshots WHERE `key` = :key LIMIT 1")
    suspend fun getImportSnapshot(key: String = ImportSnapshotEntity.ACTIVE_KEY): ImportSnapshotEntity?

    @Upsert
    suspend fun upsertImportSnapshot(snapshot: ImportSnapshotEntity)

    @Query(
        """
        SELECT name, orderIndex FROM week_days
        ORDER BY orderIndex ASC
        """
    )
    fun observeWeekDays(): Flow<List<OrderedNameRow>>

    @Query(
        """
        SELECT name, orderIndex FROM pair_times
        ORDER BY orderIndex ASC
        """
    )
    fun observePairTimes(): Flow<List<OrderedNameRow>>

    @Query(
        """
        SELECT name FROM semesters
        ORDER BY id ASC
        LIMIT 1
        """
    )
    fun observePrimarySemesterName(): Flow<String?>

    @Query(
        """
        SELECT id, name FROM study_groups
        WHERE courseNumber = :courseNumber
        AND name LIKE '%' || :query || '%'
        ORDER BY name ASC
        LIMIT 50
        """
    )
    suspend fun searchGroups(query: String, courseNumber: Int): List<NamedEntityRow>

    @Query(
        """
        SELECT DISTINCT courseNumber FROM study_groups
        WHERE courseNumber IS NOT NULL
        ORDER BY courseNumber ASC
        """
    )
    suspend fun getDistinctCourseNumbers(): List<Int>

    @Query(
        """
        SELECT id, name FROM teachers
        WHERE name LIKE '%' || :query || '%'
        ORDER BY name ASC
        LIMIT 50
        """
    )
    suspend fun searchTeachers(query: String): List<NamedEntityRow>

    @Query(
        """
        SELECT id, name FROM auditoriums
        WHERE name LIKE '%' || :query || '%'
        AND UPPER(name) != 'ДО'
        ORDER BY name ASC
        LIMIT 50
        """
    )
    suspend fun searchAuditoriums(query: String): List<NamedEntityRow>

    @Query(
        """
        SELECT
            lessons.id AS id,
            semesters.name AS semesterName,
            week_days.name AS weekDayName,
            full_pair_times.weekType AS weekType,
            pair_times.name AS timeRange,
            lessons.subjectName AS subjectName,
            teachers.name AS teacherName,
            study_groups.name AS groupName,
            auditoriums.name AS auditoriumName
        FROM lessons
        INNER JOIN full_pair_times ON full_pair_times.id = lessons.fullPairTimeId
        INNER JOIN semesters ON semesters.id = full_pair_times.semesterId
        INNER JOIN week_days ON week_days.id = full_pair_times.weekDayId
        INNER JOIN pair_times ON pair_times.id = full_pair_times.pairTimeId
        LEFT JOIN teachers ON teachers.id = lessons.teacherId
        INNER JOIN study_groups ON study_groups.id = lessons.groupId
        LEFT JOIN auditoriums ON auditoriums.id = lessons.auditoriumId
        WHERE lessons.groupId = :groupId
        ORDER BY semesters.name, week_days.orderIndex, pair_times.orderIndex, full_pair_times.weekType
        """
    )
    fun observeGroupLessons(groupId: Long): Flow<List<LessonRow>>

    @Query(
        """
        SELECT
            lessons.id AS id,
            semesters.name AS semesterName,
            week_days.name AS weekDayName,
            full_pair_times.weekType AS weekType,
            pair_times.name AS timeRange,
            lessons.subjectName AS subjectName,
            teachers.name AS teacherName,
            study_groups.name AS groupName,
            auditoriums.name AS auditoriumName
        FROM lessons
        INNER JOIN full_pair_times ON full_pair_times.id = lessons.fullPairTimeId
        INNER JOIN semesters ON semesters.id = full_pair_times.semesterId
        INNER JOIN week_days ON week_days.id = full_pair_times.weekDayId
        INNER JOIN pair_times ON pair_times.id = full_pair_times.pairTimeId
        LEFT JOIN teachers ON teachers.id = lessons.teacherId
        INNER JOIN study_groups ON study_groups.id = lessons.groupId
        LEFT JOIN auditoriums ON auditoriums.id = lessons.auditoriumId
        WHERE lessons.teacherId = :teacherId
        ORDER BY semesters.name, week_days.orderIndex, pair_times.orderIndex, full_pair_times.weekType
        """
    )
    fun observeTeacherLessons(teacherId: Long): Flow<List<LessonRow>>

    @Query(
        """
        SELECT
            lessons.id AS id,
            semesters.name AS semesterName,
            week_days.name AS weekDayName,
            full_pair_times.weekType AS weekType,
            pair_times.name AS timeRange,
            lessons.subjectName AS subjectName,
            teachers.name AS teacherName,
            study_groups.name AS groupName,
            auditoriums.name AS auditoriumName
        FROM lessons
        INNER JOIN full_pair_times ON full_pair_times.id = lessons.fullPairTimeId
        INNER JOIN semesters ON semesters.id = full_pair_times.semesterId
        INNER JOIN week_days ON week_days.id = full_pair_times.weekDayId
        INNER JOIN pair_times ON pair_times.id = full_pair_times.pairTimeId
        LEFT JOIN teachers ON teachers.id = lessons.teacherId
        INNER JOIN study_groups ON study_groups.id = lessons.groupId
        LEFT JOIN auditoriums ON auditoriums.id = lessons.auditoriumId
        WHERE lessons.auditoriumId = :auditoriumId
        ORDER BY semesters.name, week_days.orderIndex, pair_times.orderIndex, full_pair_times.weekType
        """
    )
    fun observeAuditoriumLessons(auditoriumId: Long): Flow<List<LessonRow>>

    @Query("DELETE FROM lessons")
    suspend fun clearLessons()

    @Query("DELETE FROM full_pair_times")
    suspend fun clearFullPairTimes()

    @Query("DELETE FROM pair_times")
    suspend fun clearPairTimes()

    @Query("DELETE FROM week_days")
    suspend fun clearWeekDays()

    @Query("DELETE FROM semesters")
    suspend fun clearSemesters()

    @Query("DELETE FROM teachers")
    suspend fun clearTeachers()

    @Query("DELETE FROM auditoriums")
    suspend fun clearAuditoriums()

    @Query("DELETE FROM study_groups")
    suspend fun clearStudyGroups()

    @Upsert
    suspend fun upsertSemesters(items: List<SemesterEntity>)

    @Upsert
    suspend fun upsertWeekDays(items: List<WeekDayEntity>)

    @Upsert
    suspend fun upsertPairTimes(items: List<PairTimeEntity>)

    @Upsert
    suspend fun upsertFullPairTimes(items: List<FullPairTimeEntity>)

    @Upsert
    suspend fun upsertTeachers(items: List<TeacherEntity>)

    @Upsert
    suspend fun upsertAuditoriums(items: List<AuditoriumEntity>)

    @Upsert
    suspend fun upsertStudyGroups(items: List<StudyGroupEntity>)

    @Upsert
    suspend fun upsertLessons(items: List<LessonEntity>)

    @Transaction
    suspend fun replaceImportedData(data: ImportedScheduleData) {
        clearLessons()
        clearFullPairTimes()
        clearPairTimes()
        clearWeekDays()
        clearSemesters()
        clearTeachers()
        clearAuditoriums()
        clearStudyGroups()

        if (data.semesters.isNotEmpty()) upsertSemesters(data.semesters)
        if (data.weekDays.isNotEmpty()) upsertWeekDays(data.weekDays)
        if (data.pairTimes.isNotEmpty()) upsertPairTimes(data.pairTimes)
        if (data.fullPairTimes.isNotEmpty()) upsertFullPairTimes(data.fullPairTimes)
        if (data.teachers.isNotEmpty()) upsertTeachers(data.teachers)
        if (data.auditoriums.isNotEmpty()) upsertAuditoriums(data.auditoriums)
        if (data.studyGroups.isNotEmpty()) upsertStudyGroups(data.studyGroups)
        if (data.lessons.isNotEmpty()) upsertLessons(data.lessons)
    }
}

@Database(
    entities = [
        SemesterEntity::class,
        WeekDayEntity::class,
        PairTimeEntity::class,
        FullPairTimeEntity::class,
        TeacherEntity::class,
        AuditoriumEntity::class,
        StudyGroupEntity::class,
        LessonEntity::class,
        SavedScheduleEntity::class,
        ImportSnapshotEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
}
