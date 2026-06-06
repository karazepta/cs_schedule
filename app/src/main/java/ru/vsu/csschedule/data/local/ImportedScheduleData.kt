package ru.vsu.csschedule.data.local

data class ImportedScheduleData(
    val semesters: List<SemesterEntity> = emptyList(),
    val weekDays: List<WeekDayEntity> = emptyList(),
    val pairTimes: List<PairTimeEntity> = emptyList(),
    val fullPairTimes: List<FullPairTimeEntity> = emptyList(),
    val teachers: List<TeacherEntity> = emptyList(),
    val auditoriums: List<AuditoriumEntity> = emptyList(),
    val studyGroups: List<StudyGroupEntity> = emptyList(),
    val lessons: List<LessonEntity> = emptyList(),
)
