package ru.vsu.csschedule.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.vsu.csschedule.data.local.ImportSnapshotEntity
import ru.vsu.csschedule.data.local.ImportStatus
import ru.vsu.csschedule.data.local.ScheduleOwnerType
import ru.vsu.csschedule.data.preferences.ThemePreferencesRepository
import ru.vsu.csschedule.data.repository.SavedScheduleListItem
import ru.vsu.csschedule.data.repository.ScheduleImportRepository
import ru.vsu.csschedule.data.repository.ScheduleRepository
import ru.vsu.csschedule.data.repository.SearchableScheduleEntity

data class HomeUiState(
    val searchQuery: String = "",
    val savedSchedules: List<SavedScheduleListItem> = emptyList(),
    val semesterName: String? = null,
    val importSnapshot: ImportSnapshotEntity? = null,
    val isTypePickerVisible: Boolean = false,
    val isEntityPickerVisible: Boolean = false,
    val selectedType: ScheduleOwnerType? = null,
    val entityQuery: String = "",
    val entities: List<SearchableScheduleEntity> = emptyList(),
    val availableCourses: List<Int> = emptyList(),
    val selectedCourseNumber: Int? = null,
    val isDarkTheme: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val scheduleImportRepository: ScheduleImportRepository,
    private val themePreferencesRepository: ThemePreferencesRepository,
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val isTypePickerVisible = MutableStateFlow(false)
    private val isEntityPickerVisible = MutableStateFlow(false)
    private val selectedType = MutableStateFlow<ScheduleOwnerType?>(null)
    private val entityQuery = MutableStateFlow("")
    private val availableCourses = MutableStateFlow<List<Int>>(emptyList())
    private val selectedCourseNumber = MutableStateFlow<Int?>(null)
    private val savedSchedules = searchQuery.flatMapLatest(scheduleRepository::observeSavedSchedules)
    private val semesterName = scheduleRepository.observePrimarySemesterName()
    private val importSnapshot = scheduleImportRepository.observeSnapshot()
    private val isDarkTheme = themePreferencesRepository.isDarkTheme
    private val searchableEntities = combine(
        selectedType,
        entityQuery,
        selectedCourseNumber,
    ) { type, query, courseNumber ->
        Triple(type, query, courseNumber)
    }.flatMapLatest { (type, query, courseNumber) ->
        flow {
            emit(
                if (type == null) {
                    emptyList()
                } else {
                    scheduleRepository.searchEntities(type, query, courseNumber)
                }
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<HomeUiState> = combine(
        searchQuery,
        savedSchedules,
        semesterName,
        importSnapshot,
        isTypePickerVisible,
        isEntityPickerVisible,
        selectedType,
        entityQuery,
        searchableEntities,
        availableCourses,
        selectedCourseNumber,
        isDarkTheme,
    ) { values ->
        HomeUiState(
            searchQuery = values[0] as String,
            savedSchedules = values[1] as List<SavedScheduleListItem>,
            semesterName = values[2] as String?,
            importSnapshot = values[3] as ImportSnapshotEntity?,
            isTypePickerVisible = values[4] as Boolean,
            isEntityPickerVisible = values[5] as Boolean,
            selectedType = values[6] as ScheduleOwnerType?,
            entityQuery = values[7] as String,
            entities = values[8] as List<SearchableScheduleEntity>,
            availableCourses = values[9] as List<Int>,
            selectedCourseNumber = values[10] as Int?,
            isDarkTheme = values[11] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refreshImport()
    }

    fun onSearchQueryChange(value: String) {
        searchQuery.value = value
    }

    fun onAddScheduleClick() {
        isTypePickerVisible.value = true
    }

    fun onDismissTypePicker() {
        isTypePickerVisible.value = false
    }

    fun onTypeChosen(type: ScheduleOwnerType) {
        selectedType.value = type
        entityQuery.value = ""
        isTypePickerVisible.value = false
        isEntityPickerVisible.value = true

        if (type == ScheduleOwnerType.GROUP) {
            viewModelScope.launch {
                val courses = scheduleRepository.getCourseNumbers()
                availableCourses.value = courses
                selectedCourseNumber.value = courses.firstOrNull()
            }
        } else {
            availableCourses.value = emptyList()
            selectedCourseNumber.value = null
        }
    }

    fun onDismissEntityPicker() {
        isEntityPickerVisible.value = false
        availableCourses.value = emptyList()
        selectedCourseNumber.value = null
    }

    fun onEntityQueryChange(value: String) {
        entityQuery.value = value
    }

    fun onCourseSelected(courseNumber: Int) {
        selectedCourseNumber.value = courseNumber
    }

    fun onEntityChosen(entity: SearchableScheduleEntity) {
        val type = selectedType.value ?: return
        viewModelScope.launch {
            scheduleRepository.saveSchedule(type, entity.id, entity.name)
            onDismissEntityPicker()
        }
    }

    fun onDeleteSavedSchedule(id: Long) {
        viewModelScope.launch {
            scheduleRepository.deleteSavedSchedule(id)
        }
    }

    fun refreshImport() {
        viewModelScope.launch {
            scheduleImportRepository.refreshIfNeeded()
        }
    }

    fun importStatus(snapshot: ImportSnapshotEntity?): ImportStatus {
        return snapshot?.status ?: ImportStatus.IDLE
    }

    fun onThemeChange(enabled: Boolean) {
        themePreferencesRepository.setDarkTheme(enabled)
    }

    companion object {
        fun factory(
            scheduleRepository: ScheduleRepository,
            scheduleImportRepository: ScheduleImportRepository,
            themePreferencesRepository: ThemePreferencesRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return HomeViewModel(
                        scheduleRepository,
                        scheduleImportRepository,
                        themePreferencesRepository,
                    ) as T
                }
                error("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}
