package ru.vsu.csschedule.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.vsu.csschedule.data.local.LessonRow
import ru.vsu.csschedule.data.local.NamedEntityRow
import ru.vsu.csschedule.data.local.OrderedNameRow
import ru.vsu.csschedule.data.local.ScheduleDao
import ru.vsu.csschedule.data.local.ScheduleOwnerType
import ru.vsu.csschedule.data.local.SavedScheduleEntity

data class SavedScheduleListItem(
    val id: Long,
    val type: ScheduleOwnerType,
    val referenceId: Long,
    val title: String,
)

data class SearchableScheduleEntity(
    val id: Long,
    val name: String,
)

data class OrderedNameItem(
    val name: String,
    val orderIndex: Int,
)

class ScheduleRepository(
    private val scheduleDao: ScheduleDao,
) {
    fun observeSavedSchedules(query: String): Flow<List<SavedScheduleListItem>> {
        return scheduleDao.observeSavedSchedules(query.trim()).map { items ->
            items.map { item ->
                SavedScheduleListItem(
                    id = item.id,
                    type = item.type,
                    referenceId = item.referenceId,
                    title = item.displayName,
                )
            }
        }
    }

    suspend fun getCourseNumbers(): List<Int> = scheduleDao.getDistinctCourseNumbers()

    suspend fun searchEntities(
        type: ScheduleOwnerType,
        query: String,
        courseNumber: Int? = null,
    ): List<SearchableScheduleEntity> {
        val rows: List<NamedEntityRow> = when (type) {
            ScheduleOwnerType.GROUP -> {
                val course = courseNumber
                    ?: return emptyList()
                scheduleDao.searchGroups(query.trim(), course)
            }
            ScheduleOwnerType.TEACHER -> scheduleDao.searchTeachers(query.trim())
            ScheduleOwnerType.AUDITORIUM -> scheduleDao.searchAuditoriums(query.trim())
        }

        return rows.map { SearchableScheduleEntity(id = it.id, name = it.name) }
    }

    suspend fun saveSchedule(type: ScheduleOwnerType, referenceId: Long, displayName: String) {
        scheduleDao.insertSavedSchedule(
            SavedScheduleEntity(
                type = type,
                referenceId = referenceId,
                displayName = displayName,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun deleteSavedSchedule(id: Long) {
        scheduleDao.deleteSavedSchedule(id)
    }

    fun observeLessons(type: ScheduleOwnerType, referenceId: Long): Flow<List<LessonRow>> {
        return when (type) {
            ScheduleOwnerType.GROUP -> scheduleDao.observeGroupLessons(referenceId)
            ScheduleOwnerType.TEACHER -> scheduleDao.observeTeacherLessons(referenceId)
            ScheduleOwnerType.AUDITORIUM -> scheduleDao.observeAuditoriumLessons(referenceId)
        }
    }

    fun observeWeekDays(): Flow<List<OrderedNameItem>> {
        return scheduleDao.observeWeekDays().map(::mapOrderedRows)
    }

    fun observePairTimes(): Flow<List<OrderedNameItem>> {
        return scheduleDao.observePairTimes().map(::mapOrderedRows)
    }

    fun observePrimarySemesterName(): Flow<String?> = scheduleDao.observePrimarySemesterName()

    private fun mapOrderedRows(rows: List<OrderedNameRow>): List<OrderedNameItem> {
        return rows.map { OrderedNameItem(name = it.name, orderIndex = it.orderIndex) }
    }
}
