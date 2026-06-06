package ru.vsu.csschedule.ui.details

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ru.vsu.csschedule.data.local.LessonRow
import ru.vsu.csschedule.data.local.ScheduleOwnerType
import ru.vsu.csschedule.data.local.WeekType
import ru.vsu.csschedule.data.repository.OrderedNameItem
import ru.vsu.csschedule.data.repository.ScheduleRepository

data class LessonUiItem(
    val id: Long,
    val subjectName: String,
    val teacherName: String?,
    val groupName: String,
    val auditoriumName: String?,
)

data class PairScheduleUiItem(
    val timeRange: String,
    val numeratorLesson: LessonUiItem?,
    val denominatorLesson: LessonUiItem?,
)

data class DayScheduleUiItem(
    val weekDayName: String,
    val pairs: List<PairScheduleUiItem>,
)

data class ScheduleDetailsUiState(
    val semesterName: String? = null,
    val days: List<DayScheduleUiItem> = emptyList(),
)

class ScheduleDetailsViewModel(
    savedStateHandle: SavedStateHandle,
    scheduleRepository: ScheduleRepository,
) : ViewModel() {
    val title: String = Uri.decode(savedStateHandle.get<String>("title").orEmpty())

    private val type = ScheduleOwnerType.valueOf(
        savedStateHandle.get<String>("type") ?: ScheduleOwnerType.GROUP.name
    )
    private val referenceId = savedStateHandle.get<Long>("referenceId") ?: 0L

    val uiState: StateFlow<ScheduleDetailsUiState> = combine(
        scheduleRepository.observeLessons(type, referenceId),
        scheduleRepository.observeWeekDays(),
        scheduleRepository.observePairTimes(),
        scheduleRepository.observePrimarySemesterName(),
    ) { lessons, weekDays, pairTimes, semesterName ->
        ScheduleDetailsUiState(
            semesterName = semesterName,
            days = buildDays(lessons, weekDays, pairTimes),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleDetailsUiState())

    private fun buildDays(
        lessons: List<LessonRow>,
        weekDays: List<OrderedNameItem>,
        pairTimes: List<OrderedNameItem>,
    ): List<DayScheduleUiItem> {
        if (weekDays.isEmpty() || pairTimes.isEmpty()) return emptyList()

        return weekDays.sortedBy { it.orderIndex }.map { day ->
            DayScheduleUiItem(
                weekDayName = day.name,
                pairs = pairTimes.sortedBy { it.orderIndex }.map { pairTime ->
                    PairScheduleUiItem(
                        timeRange = pairTime.name,
                        numeratorLesson = lessons.firstOrNull {
                            it.weekDayName == day.name &&
                                it.timeRange == pairTime.name &&
                                it.weekType == WeekType.NUMERATOR
                        }?.toUiItem(),
                        denominatorLesson = lessons.firstOrNull {
                            it.weekDayName == day.name &&
                                it.timeRange == pairTime.name &&
                                it.weekType == WeekType.DENOMINATOR
                        }?.toUiItem(),
                    )
                },
            )
        }
    }

    private fun LessonRow.toUiItem(): LessonUiItem {
        return LessonUiItem(
            id = id,
            subjectName = subjectName,
            teacherName = teacherName,
            groupName = groupName,
            auditoriumName = auditoriumName,
        )
    }

    companion object {
        fun factory(
            scheduleRepository: ScheduleRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                if (modelClass.isAssignableFrom(ScheduleDetailsViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return ScheduleDetailsViewModel(
                        savedStateHandle = extras.createSavedStateHandle(),
                        scheduleRepository = scheduleRepository,
                    ) as T
                }
                error("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}
