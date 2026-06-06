package ru.vsu.csschedule.data.importing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import ru.vsu.csschedule.data.local.AuditoriumEntity
import ru.vsu.csschedule.data.local.FullPairTimeEntity
import ru.vsu.csschedule.data.local.ImportedScheduleData
import ru.vsu.csschedule.data.local.LessonEntity
import ru.vsu.csschedule.data.local.PairTimeEntity
import ru.vsu.csschedule.data.local.SemesterEntity
import ru.vsu.csschedule.data.local.StudyGroupEntity
import ru.vsu.csschedule.data.local.TeacherEntity
import ru.vsu.csschedule.data.local.WeekDayEntity
import ru.vsu.csschedule.data.local.WeekType
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class RealSheetManualMapper : SheetManualMapper {
    override suspend fun map(snapshot: SheetDownloadSnapshot): SheetMappingResult = withContext(Dispatchers.IO) {
        val file = File(snapshot.localFilePath)
        if (!file.exists()) {
            return@withContext SheetMappingResult.Failure("Downloaded file does not exist: ${snapshot.localFilePath}")
        }

        if (!file.extension.equals("xlsx", ignoreCase = true)) {
            return@withContext SheetMappingResult.Failure(
                "Current mapper requires XLSX because CSV does not preserve merged cells and empty ranges."
            )
        }

        runCatching { parseWorkbook(file) }
            .fold(
                onSuccess = { parsed ->
                    if (parsed.lessons.isEmpty()) {
                        SheetMappingResult.Failure("Workbook was parsed but no lessons were recognized")
                    } else {
                        SheetMappingResult.Success(
                            data = buildImportedScheduleData(parsed),
                            summary = buildSummary(parsed),
                        )
                    }
                },
                onFailure = { error ->
                    SheetMappingResult.Failure(error.message ?: error.toString())
                }
            )
    }

    private fun parseWorkbook(file: File): ParsedWorkbook {
        val workbook = XlsxWorkbookParser().parse(file)
        val grid = workbook.sheet.grid
        val groupBindings = extractGroupBindings(grid)

        require(groupBindings.isNotEmpty()) { "No group columns were found in row 2" }

        val dayBlocks = buildDayBlocks(grid)
        val parsedLessons = mutableListOf<ParsedLesson>()
        val pairTimeOrder = linkedMapOf<String, Int>()
        val seenDays = linkedMapOf<String, Int>()

        for (dayBlock in dayBlocks) {
            val pairRows = dayBlock.pairRows()
            if (pairRows.isEmpty()) continue

            pairRows.forEachIndexed { pairIndex, (numeratorRow, denominatorRow) ->
                val timeRange = buildTimeRange(grid.effectiveText(numeratorRow, 2), grid.effectiveText(denominatorRow, 2))
                if (timeRange.isBlank()) return@forEachIndexed

                seenDays.putIfAbsent(dayBlock.name, seenDays.size + 1)

                pairTimeOrder.putIfAbsent(timeRange, pairIndex + 1)

                groupBindings.forEach { binding ->
                    parseLessonCell(grid.effectiveText(numeratorRow, binding.columnIndex))?.let { lesson ->
                        parsedLessons += ParsedLesson(
                            groupName = binding.groupName,
                            courseNumber = binding.courseNumber,
                            weekDayName = dayBlock.name,
                            weekType = WeekType.NUMERATOR,
                            timeRange = timeRange,
                            subjectName = lesson.subjectName,
                            teacherName = lesson.teacherName,
                            auditoriumName = lesson.auditoriumName,
                        )
                    }

                    parseLessonCell(grid.effectiveText(denominatorRow, binding.columnIndex))?.let { lesson ->
                        parsedLessons += ParsedLesson(
                            groupName = binding.groupName,
                            courseNumber = binding.courseNumber,
                            weekDayName = dayBlock.name,
                            weekType = WeekType.DENOMINATOR,
                            timeRange = timeRange,
                            subjectName = lesson.subjectName,
                            teacherName = lesson.teacherName,
                            auditoriumName = lesson.auditoriumName,
                        )
                    }
                }
            }
        }

        return ParsedWorkbook(
            semesterName = workbook.sheet.name.ifBlank { "Imported workbook" },
            days = seenDays.keys.toList(),
            pairTimes = pairTimeOrder,
            lessons = parsedLessons,
        )
    }

    private fun extractGroupBindings(grid: SheetGrid): List<ColumnBinding> {
        val bindings = mutableListOf<ColumnBinding>()
        var column = 3

        while (column <= grid.maxCol) {
            val groupLabel = normalizeCellText(grid.effectiveText(2, column))
            if (groupLabel.isBlank()) {
                column++
                continue
            }

            val headerRange = grid.findMergedRange(2, column)
            val span = headerRange?.let { it.endCol - it.startCol + 1 } ?: 1

            val courseLabel = resolveCourseLabel(grid, column)
            val courseNumber = extractCourseNumber(courseLabel)?.toIntOrNull()

            if (span == 1) {
                bindings += ColumnBinding(
                    columnIndex = column,
                    groupName = formatGroupDisplayName(groupLabel, subgroupIndex = null),
                    courseNumber = courseNumber,
                )
            } else {
                for (offset in 0 until span) {
                    bindings += ColumnBinding(
                        columnIndex = column + offset,
                        groupName = formatGroupDisplayName(groupLabel, offset + 1),
                        courseNumber = courseNumber,
                    )
                }
            }

            column += span
        }

        return bindings
    }

    private fun buildDayBlocks(grid: SheetGrid): List<DayBlock> {
        val configuredBlocks = listOf(
            DayBlockSeed("Понедельник", 5, 22),
            DayBlockSeed("Вторник", 22, 39),
            DayBlockSeed("Среда", 39, 56),
            DayBlockSeed("Четверг", 56, 73),
            DayBlockSeed("Пятница", 73, 90),
            DayBlockSeed("Суббота", 90, null),
        )

        return configuredBlocks.mapNotNull { seed ->
            val endRow = when (seed.nextStartRow) {
                null -> findLastDayEndRow(grid, seed.startRow)
                else -> seed.nextStartRow - 2
            }

            if (endRow < seed.startRow) {
                null
            } else {
                val rawDayName = normalizeCellText(grid.effectiveText(seed.startRow, 1))
                DayBlock(
                    name = rawDayName.ifBlank { seed.defaultName },
                    startRow = seed.startRow,
                    endRow = if ((endRow - seed.startRow + 1) % 2 == 0) endRow else endRow - 1,
                )
            }
        }
    }

    private fun findLastDayEndRow(grid: SheetGrid, startRow: Int): Int {
        var lastRow = startRow - 1

        for (row in startRow..grid.maxRow) {
            if (grid.effectiveText(row, 2).isNotBlank()) {
                lastRow = row
            }
        }

        return lastRow
    }

    private fun buildTimeRange(numeratorValue: String, denominatorValue: String): String {
        val numerator = normalizeCellText(numeratorValue)
        val denominator = normalizeCellText(denominatorValue)

        if (numerator.isBlank() && denominator.isBlank()) return ""

        val fullRange = listOf(numerator, denominator).firstOrNull(::isFullTimeRange)
        if (fullRange != null) return fullRange

        if (numerator.isNotBlank() && numerator == denominator) return numerator

        return when {
            numerator.isBlank() -> denominator
            denominator.isBlank() -> numerator
            else -> "$numerator - $denominator"
        }
    }

    private fun isFullTimeRange(value: String): Boolean {
        return TIME_RANGE_SEPARATOR_REGEX.containsMatchIn(value)
    }

    private fun parseLessonCell(rawValue: String): ParsedCellLesson? {
        val normalized = normalizeCellText(rawValue)
        if (normalized.isBlank()) return null

        val lowerCase = normalized.lowercase(Locale.getDefault())
        if (lowerCase.startsWith("русский язык для иностранцев")) return null

        val teacher = TEACHER_REGEX.find(normalized)?.value?.trim()
        val auditoriumMatch = AUDITORIUM_REGEX.findAll(normalized).lastOrNull()
        val auditorium = auditoriumMatch?.groupValues?.get(1)?.trim()

        var subject = normalized
            .replace(TEACHER_REGEX, " ")
            .replace(SUBJECT_ID_REGEX, " ")
            .replace(TITLE_REGEX, " ")
            .trim()

        auditorium?.let {
            subject = subject.replace(auditoriumMatch!!.value, " ").trim()
        }

        subject = subject
            .replace(LEADING_TRAILING_SEPARATORS, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim(' ', ',', ';', '-', '!')

        if (subject.isBlank()) {
            subject = normalized
        }

        return ParsedCellLesson(
            subjectName = subject,
            teacherName = teacher,
            auditoriumName = auditorium,
        )
    }

    private fun buildImportedScheduleData(parsed: ParsedWorkbook): ImportedScheduleData {
        val semester = SemesterEntity(id = 1, name = parsed.semesterName)

        val weekDays = parsed.days.mapIndexed { index, dayName ->
            WeekDayEntity(
                id = index + 1L,
                name = dayName,
                orderIndex = index,
            )
        }

        val pairTimes = parsed.pairTimes.entries.sortedBy { it.value }.mapIndexed { index, entry ->
            PairTimeEntity(
                id = index + 1L,
                name = entry.key,
                orderIndex = index,
            )
        }

        val teachers = parsed.lessons.mapNotNull { it.teacherName }
            .distinct()
            .sorted()
            .mapIndexed { index, name -> TeacherEntity(id = index + 1L, name = name) }

        val auditoriums = parsed.lessons.mapNotNull { it.auditoriumName }
            .filterNot { it.equals(REMOTE_AUDITORIUM_NAME, ignoreCase = true) }
            .distinct()
            .sorted()
            .mapIndexed { index, name -> AuditoriumEntity(id = index + 1L, name = name) }

        val studyGroups = parsed.lessons
            .map { GroupKey(name = it.groupName, courseNumber = it.courseNumber) }
            .distinct()
            .sortedWith(compareBy<GroupKey> { it.courseNumber }.thenBy { it.name })
            .mapIndexed { index, key ->
                StudyGroupEntity(
                    id = index + 1L,
                    name = key.name,
                    courseNumber = key.courseNumber,
                )
            }

        val weekDayIds = weekDays.associateBy({ it.name }, { it.id })
        val pairTimeIds = pairTimes.associateBy({ it.name }, { it.id })
        val teacherIds = teachers.associateBy({ it.name }, { it.id })
        val auditoriumIds = auditoriums.associateBy({ it.name }, { it.id })
        val groupIds = studyGroups.associateBy({ GroupKey(it.name, it.courseNumber) }, { it.id })

        val fullPairTimeIds = linkedMapOf<FullPairKey, Long>()
        val fullPairTimes = mutableListOf<FullPairTimeEntity>()

        parsed.days.forEach { dayName ->
            parsed.pairTimes.entries.sortedBy { it.value }.forEach { entry ->
                val pairTimeId = requireNotNull(pairTimeIds[entry.key]) { "Missing pair time id for ${entry.key}" }
                listOf(WeekType.NUMERATOR, WeekType.DENOMINATOR).forEach { weekType ->
                    val key = FullPairKey(dayName = dayName, weekType = weekType, timeRange = entry.key)
                    val id = fullPairTimeIds.size + 1L
                    fullPairTimeIds[key] = id
                    fullPairTimes += FullPairTimeEntity(
                        id = id,
                        semesterId = semester.id,
                        weekType = weekType,
                        weekDayId = requireNotNull(weekDayIds[dayName]) { "Missing week day id for $dayName" },
                        pairTimeId = pairTimeId,
                    )
                }
            }
        }

        val lessons = parsed.lessons.mapIndexed { index, lesson ->
            LessonEntity(
                id = index + 1L,
                fullPairTimeId = requireNotNull(
                    fullPairTimeIds[FullPairKey(lesson.weekDayName, lesson.weekType, lesson.timeRange)]
                ) {
                    "Missing full pair time for ${lesson.weekDayName} ${lesson.timeRange} ${lesson.weekType}"
                },
                subjectName = lesson.subjectName,
                auditoriumId = lesson.auditoriumName?.let { auditoriumIds[it] },
                groupId = requireNotNull(
                    groupIds[GroupKey(lesson.groupName, lesson.courseNumber)]
                ) { "Missing group id for ${lesson.groupName} course ${lesson.courseNumber}" },
                teacherId = lesson.teacherName?.let { teacherIds[it] },
            )
        }

        return ImportedScheduleData(
            semesters = listOf(semester),
            weekDays = weekDays,
            pairTimes = pairTimes,
            fullPairTimes = fullPairTimes,
            teachers = teachers,
            auditoriums = auditoriums,
            studyGroups = studyGroups,
            lessons = lessons,
        )
    }

    private fun buildSummary(parsed: ParsedWorkbook): String {
        val groups = parsed.lessons.map { it.groupName }.distinct().size
        val teachers = parsed.lessons.mapNotNull { it.teacherName }.distinct().size
        val auditoriums = parsed.lessons.mapNotNull { it.auditoriumName }.distinct().size

        return "Imported ${parsed.lessons.size} lessons from XLSX: $groups groups, $teachers teachers, $auditoriums auditoriums."
    }

    private fun normalizeCellText(value: String): String {
        return value
            .replace('\u00A0', ' ')
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }

    private fun formatGroupDisplayName(groupLabel: String, subgroupIndex: Int?): String {
        val groupNumber = extractGroupNumber(groupLabel)
        return when (subgroupIndex) {
            null -> "$groupNumber группа"
            else -> "$groupNumber.$subgroupIndex группа"
        }
    }

    private fun extractGroupNumber(groupLabel: String): String {
        val normalized = normalizeCellText(groupLabel)
        return GROUP_NUMBER_REGEX.find(normalized)?.groupValues?.get(1) ?: normalized
    }

    private fun extractCourseNumber(courseLabel: String): String? {
        if (courseLabel.isBlank()) return null
        return COURSE_NUMBER_REGEX.find(courseLabel)?.groupValues?.get(1)
    }

    private fun resolveCourseLabel(grid: SheetGrid, column: Int): String {
        val direct = normalizeCellText(grid.effectiveText(1, column))
        if (direct.isNotBlank()) return direct

        for (col in column downTo 3) {
            val merge = grid.findMergedRange(1, col) ?: continue
            if (column > merge.endCol) continue
            val label = normalizeCellText(grid.effectiveText(1, merge.startCol))
            if (label.isNotBlank()) return label
        }

        return ""
    }

    private data class ParsedWorkbook(
        val semesterName: String,
        val days: List<String>,
        val pairTimes: LinkedHashMap<String, Int>,
        val lessons: List<ParsedLesson>,
    )

    private data class GroupKey(
        val name: String,
        val courseNumber: Int?,
    )

    private data class ParsedLesson(
        val groupName: String,
        val courseNumber: Int?,
        val weekDayName: String,
        val weekType: WeekType,
        val timeRange: String,
        val subjectName: String,
        val teacherName: String?,
        val auditoriumName: String?,
    )

    private data class ParsedCellLesson(
        val subjectName: String,
        val teacherName: String?,
        val auditoriumName: String?,
    )

    private data class ColumnBinding(
        val columnIndex: Int,
        val groupName: String,
        val courseNumber: Int?,
    )

    private data class DayBlockSeed(
        val defaultName: String,
        val startRow: Int,
        val nextStartRow: Int?,
    )

    private data class DayBlock(
        val name: String,
        val startRow: Int,
        val endRow: Int,
    ) {
        fun pairRows(): List<Pair<Int, Int>> {
            if (endRow < startRow) return emptyList()

            val rows = mutableListOf<Pair<Int, Int>>()
            var row = startRow
            while (row + 1 <= endRow) {
                rows += row to (row + 1)
                row += 2
            }
            return rows
        }
    }

    private data class FullPairKey(
        val dayName: String,
        val weekType: WeekType,
        val timeRange: String,
    )

    companion object {
        private val MULTI_SPACE_REGEX = Regex("\\s+")
        private val SUBJECT_ID_REGEX = Regex("""\bid\s*=\s*\d+\b""", RegexOption.IGNORE_CASE)
        private val TITLE_REGEX = Regex("""(?:асс\.|доц\.|проф\.|ст\.преп\.|преп\.)""", RegexOption.IGNORE_CASE)
        private val TEACHER_REGEX = Regex("""[А-ЯЁ][а-яё-]+\s+[А-ЯЁ]\.[А-ЯЁ]\.""")
        private val AUDITORIUM_REGEX = Regex("""(?:ауд\.?\s*)?((?:\d{2,4}[А-Яа-яA-Za-z]?|ДО))\b""")
        private val LEADING_TRAILING_SEPARATORS = Regex("""[|]+""")
        private val COURSE_NUMBER_REGEX = Regex("""(\d+)\s*курс""", RegexOption.IGNORE_CASE)
        private val GROUP_NUMBER_REGEX = Regex("""(\d+)""")
        private val TIME_RANGE_SEPARATOR_REGEX = Regex("""\s+[-–—]\s+""")
        private const val REMOTE_AUDITORIUM_NAME = "ДО"
    }
}

private class XlsxWorkbookParser {
    fun parse(file: File): ParsedXlsxWorkbook {
        ZipFile(file).use { zipFile ->
            val sharedStrings = parseSharedStrings(zipFile)
            val workbookInfo = parseWorkbookInfo(zipFile)
            val sheetEntry = findEntry(zipFile, workbookInfo.sheetPath)
            val sheet = zipFile.getInputStream(sheetEntry).use { input ->
                parseSheet(input, workbookInfo.sheetName, sharedStrings)
            }
            return ParsedXlsxWorkbook(sheet = sheet)
        }
    }

    private fun parseSharedStrings(zipFile: ZipFile): List<String> {
        val entry = zipFile.getEntry("xl/sharedStrings.xml") ?: return emptyList()
        return zipFile.getInputStream(entry).use { input ->
            val parser = newParser(input)
            val values = mutableListOf<String>()
            val buffer = StringBuilder()
            var insideSi = false

            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "si") {
                            insideSi = true
                            buffer.clear()
                        } else if (insideSi && parser.name == "t") {
                            buffer.append(parser.nextText())
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        if (parser.name == "si") {
                            values += buffer.toString()
                            insideSi = false
                        }
                    }
                }
            }

            values
        }
    }

    private fun parseWorkbookInfo(zipFile: ZipFile): WorkbookInfo {
        val relationships = parseWorkbookRelationships(zipFile)
        val workbookEntry = findEntry(zipFile, "xl/workbook.xml")

        return zipFile.getInputStream(workbookEntry).use { input ->
            val parser = newParser(input)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                    val sheetName = parser.getAttributeValue(null, "name").orEmpty()
                    val relationId = parser.attributeValue("id")
                    val target = relationships[relationId]
                        ?: error("Workbook relationship $relationId was not found")

                    return WorkbookInfo(
                        sheetName = sheetName,
                        sheetPath = normalizeWorkbookTarget(target),
                    )
                }
            }

            error("Workbook does not contain any sheets")
        }
    }

    private fun parseWorkbookRelationships(zipFile: ZipFile): Map<String, String> {
        val entry = findEntry(zipFile, "xl/_rels/workbook.xml.rels")
        return zipFile.getInputStream(entry).use { input ->
            val parser = newParser(input)
            val relationships = mutableMapOf<String, String>()
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                    val id = parser.getAttributeValue(null, "Id").orEmpty()
                    val target = parser.getAttributeValue(null, "Target").orEmpty()
                    if (id.isNotBlank() && target.isNotBlank()) {
                        relationships[id] = target
                    }
                }
            }
            relationships
        }
    }

    private fun parseSheet(
        input: InputStream,
        sheetName: String,
        sharedStrings: List<String>,
    ): ParsedXlsxSheet {
        val parser = newParser(input)
        val cells = mutableMapOf<CellRef, String>()
        val mergedRanges = mutableListOf<CellRange>()
        var maxRow = 0
        var maxCol = 0
        var currentCellRef: CellRef? = null
        var currentCellType: String? = null
        var currentValue: String? = null
        var insideInlineString = false

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "c" -> {
                        currentCellRef = parser.getAttributeValue(null, "r")?.let(::parseCellRef)
                        currentCellType = parser.getAttributeValue(null, "t")
                        currentValue = null
                    }

                    "v" -> currentValue = parser.nextText()
                    "t" -> if (currentCellType == "inlineStr" || insideInlineString) {
                        currentValue = (currentValue ?: "") + parser.nextText()
                    }

                    "is" -> insideInlineString = true

                    "mergeCell" -> {
                        parser.getAttributeValue(null, "ref")
                            ?.takeIf { it.isNotBlank() }
                            ?.let { parseCellRange(it) }
                            ?.also { range ->
                                mergedRanges += range
                                maxRow = maxOf(maxRow, range.endRow)
                                maxCol = maxOf(maxCol, range.endCol)
                            }
                    }
                }

                XmlPullParser.END_TAG -> when (parser.name) {
                    "c" -> {
                        val ref = currentCellRef
                        if (ref != null) {
                            val text = resolveCellValue(currentValue, currentCellType, sharedStrings)
                            if (text.isNotBlank()) {
                                cells[ref] = text
                                maxRow = maxOf(maxRow, ref.row)
                                maxCol = maxOf(maxCol, ref.col)
                            }
                        }
                        currentCellRef = null
                        currentCellType = null
                        currentValue = null
                    }

                    "is" -> insideInlineString = false
                }
            }
        }

        return ParsedXlsxSheet(
            name = sheetName,
            grid = SheetGrid(
                cells = cells,
                mergedRanges = mergedRanges,
                maxRow = maxRow,
                maxCol = maxCol,
            )
        )
    }

    private fun resolveCellValue(rawValue: String?, type: String?, sharedStrings: List<String>): String {
        val value = rawValue.orEmpty()
        return when (type) {
            "s" -> sharedStrings.getOrNull(value.toIntOrNull() ?: -1).orEmpty()
            "inlineStr" -> value
            else -> normalizeNumericString(value)
        }
    }

    private fun normalizeNumericString(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        val number = trimmed.toDoubleOrNull() ?: return trimmed
        return if (number % 1.0 == 0.0) {
            number.toLong().toString()
        } else {
            trimmed
        }
    }

    private fun normalizeWorkbookTarget(target: String): String {
        return when {
            target.startsWith("/xl/") -> target.removePrefix("/")
            target.startsWith("xl/") -> target
            else -> "xl/$target"
        }
    }

    private fun findEntry(zipFile: ZipFile, path: String): ZipEntry {
        return zipFile.getEntry(path) ?: error("Archive entry was not found: $path")
    }

    private fun newParser(input: InputStream): XmlPullParser {
        return XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(input, null)
        }
    }

    private fun XmlPullParser.attributeValue(localName: String): String {
        return getAttributeValue(null, localName)
            ?: (0 until attributeCount)
                .firstOrNull {
                    val name = getAttributeName(it)
                    name == localName || name.endsWith(":$localName")
                }
                ?.let(::getAttributeValue)
            ?: ""
    }

    private fun parseCellRef(cellRef: String): CellRef {
        val match = CELL_REF_REGEX.matchEntire(cellRef.trim())
            ?: error("Invalid cell reference: $cellRef")
        val colText = match.groupValues[1]
        val row = match.groupValues[2].toInt()
        return CellRef(row = row, col = columnIndex(colText))
    }

    private fun parseCellRange(rangeRef: String): CellRange {
        val parts = rangeRef.split(':')
        val start = parseCellRef(parts[0])
        val end = parseCellRef(parts.getOrElse(1) { parts[0] })
        return CellRange(
            startRow = minOf(start.row, end.row),
            endRow = maxOf(start.row, end.row),
            startCol = minOf(start.col, end.col),
            endCol = maxOf(start.col, end.col),
        )
    }

    private fun columnIndex(text: String): Int {
        var value = 0
        text.uppercase(Locale.US).forEach { char ->
            value = value * 26 + (char.code - 'A'.code + 1)
        }
        return value
    }

    private data class WorkbookInfo(
        val sheetName: String,
        val sheetPath: String,
    )

    companion object {
        private val CELL_REF_REGEX = Regex("""([A-Za-z]+)(\d+)""")
    }
}

private data class ParsedXlsxWorkbook(
    val sheet: ParsedXlsxSheet,
)

private data class ParsedXlsxSheet(
    val name: String,
    val grid: SheetGrid,
)

private data class CellRef(
    val row: Int,
    val col: Int,
)

private data class CellRange(
    val startRow: Int,
    val endRow: Int,
    val startCol: Int,
    val endCol: Int,
) {
    fun contains(row: Int, col: Int): Boolean {
        return row in startRow..endRow && col in startCol..endCol
    }
}

private class SheetGrid(
    private val cells: Map<CellRef, String>,
    private val mergedRanges: List<CellRange>,
    val maxRow: Int,
    val maxCol: Int,
) {
    fun effectiveText(row: Int, col: Int): String {
        val direct = cells[CellRef(row, col)]
        if (!direct.isNullOrBlank()) return direct

        val range = findMergedRange(row, col) ?: return ""
        return cells[CellRef(range.startRow, range.startCol)].orEmpty()
    }

    fun findMergedRange(row: Int, col: Int): CellRange? {
        return mergedRanges.firstOrNull { it.contains(row, col) }
    }
}
