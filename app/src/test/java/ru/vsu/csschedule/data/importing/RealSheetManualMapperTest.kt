package ru.vsu.csschedule.data.importing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.vsu.csschedule.data.local.ImportedScheduleData
import ru.vsu.csschedule.data.local.WeekType
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class RealSheetManualMapperTest {
    @Test
    fun map_parsesMergedGroupsAndOptionalFieldsFromXlsx() = runBlocking {
        val file = createWorkbookFile()
        val mapper = RealSheetManualMapper()

        val result = mapper.map(
            SheetDownloadSnapshot(
                sourcePageUrl = "https://example.test",
                sheetUrl = "https://example.test/workbook.xlsx",
                localFilePath = file.absolutePath,
                contentHash = "test-hash",
                downloadedAt = 0L,
            )
        )

        assertTrue(
            (result as? SheetMappingResult.Failure)?.summary
                ?: (result as? SheetMappingResult.NotConfigured)?.summary
                ?: "mapping succeeded",
            result is SheetMappingResult.Success,
        )
        val data = (result as SheetMappingResult.Success).data

        assertEquals(listOf("Schedule"), data.semesters.map { it.name })
        assertEquals(listOf("Понедельник"), data.weekDays.map { it.name })
        assertEquals(listOf("8:00 - 9:35"), data.pairTimes.map { it.name })
        assertEquals(2, data.fullPairTimes.size)
        assertEquals(
            listOf("10 группа", "9.1 группа", "9.2 группа"),
            data.studyGroups.map { it.name }.sorted(),
        )
        assertTrue(data.studyGroups.all { it.courseNumber == 1 })
        assertEquals(listOf("Павлова Е.А.", "Сирота Е.А."), data.teachers.map { it.name }.sorted())
        assertEquals(listOf("307п", "505п"), data.auditoriums.map { it.name }.sorted())
        assertEquals(5, data.lessons.size)

        assertCommonLesson(data, groupName = "9.1 группа", weekType = WeekType.NUMERATOR)
        assertCommonLesson(data, groupName = "9.2 группа", weekType = WeekType.NUMERATOR)
        assertDenominatorLesson(data, groupName = "9.1 группа")
        assertDenominatorLesson(data, groupName = "9.2 группа")
        assertSpecialLesson(data, groupName = "10 группа")
    }

    @Test
    fun map_doesNotDuplicateTimeRangeWhenBothRowsContainFullRange() = runBlocking {
        val file = createWorkbookFile(
            numeratorTime = "8:00 - 9:35",
            denominatorTime = "8:00 - 9:35",
        )
        val mapper = RealSheetManualMapper()

        val result = mapper.map(
            SheetDownloadSnapshot(
                sourcePageUrl = "https://example.test",
                sheetUrl = "https://example.test/workbook.xlsx",
                localFilePath = file.absolutePath,
                contentHash = "test-hash-full-time",
                downloadedAt = 0L,
            )
        )

        assertTrue(result is SheetMappingResult.Success)
        assertEquals(listOf("8:00 - 9:35"), (result as SheetMappingResult.Success).data.pairTimes.map { it.name })
    }

    private fun assertCommonLesson(data: ImportedScheduleData, groupName: String, weekType: WeekType) {
        val lesson = requireLesson(data, groupName, weekType)
        assertEquals("ТФКП", lesson.subjectName)
        assertEquals("Сирота Е.А.", data.teacherName(lesson.teacherId))
        assertEquals("505п", data.auditoriumName(lesson.auditoriumId))
    }

    private fun assertDenominatorLesson(data: ImportedScheduleData, groupName: String) {
        val lesson = requireLesson(data, groupName, WeekType.DENOMINATOR)
        assertEquals("Экономика и финансовая грамотность", lesson.subjectName)
        assertEquals("Павлова Е.А.", data.teacherName(lesson.teacherId))
        assertEquals("307п", data.auditoriumName(lesson.auditoriumId))
    }

    private fun assertSpecialLesson(data: ImportedScheduleData, groupName: String) {
        val lesson = requireLesson(data, groupName, WeekType.NUMERATOR)
        assertEquals("ВУЦ", lesson.subjectName)
        assertNull(lesson.teacherId)
        assertNull(lesson.auditoriumId)
    }

    private fun requireLesson(
        data: ImportedScheduleData,
        groupName: String,
        weekType: WeekType,
    ) = data.lessons.firstOrNull { lesson ->
        val group = data.studyGroups.firstOrNull { it.id == lesson.groupId }?.name
        val fullPairTime = data.fullPairTimes.firstOrNull { it.id == lesson.fullPairTimeId }
        group == groupName && fullPairTime?.weekType == weekType
    }.also { assertNotNull("Lesson for $groupName / $weekType was not found", it) }!!

    private fun ImportedScheduleData.teacherName(id: Long?): String? {
        return teachers.firstOrNull { it.id == id }?.name
    }

    private fun ImportedScheduleData.auditoriumName(id: Long?): String? {
        return auditoriums.firstOrNull { it.id == id }?.name
    }

    private fun createWorkbookFile(
        numeratorTime: String = "8:00",
        denominatorTime: String = "9:35",
    ): File {
        val file = File.createTempFile("csschedule-mapper-test", ".xlsx")
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.writeEntry(
                "[Content_Types].xml",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>
                """.trimIndent()
            )
            zip.writeEntry(
                "_rels/.rels",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
                """.trimIndent()
            )
            zip.writeEntry(
                "xl/workbook.xml",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <sheets>
                        <sheet name="Schedule" sheetId="1" r:id="rId1"/>
                    </sheets>
                </workbook>
                """.trimIndent()
            )
            zip.writeEntry(
                "xl/_rels/workbook.xml.rels",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                </Relationships>
                """.trimIndent()
            )
            zip.writeEntry(
                "xl/worksheets/sheet1.xml",
                buildSheetXml(numeratorTime = numeratorTime, denominatorTime = denominatorTime),
            )
        }
        file.deleteOnExit()
        return file
    }

    private fun buildSheetXml(
        numeratorTime: String = "8:00",
        denominatorTime: String = "9:35",
    ): String {
        val cells = listOf(
            inlineCell("C1", "1 курс"),
            inlineCell("C2", "9"),
            inlineCell("E2", "10"),
            inlineCell("A5", "Понедельник"),
            inlineCell("B5", numeratorTime),
            inlineCell("B6", denominatorTime),
            inlineCell("C5", "ТФКП доц. Сирота Е.А. 505п"),
            inlineCell("C6", "Экономика и финансовая грамотность Павлова Е.А. 307п"),
            inlineCell("E5", "ВУЦ"),
        )

        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <sheetData>
                    <row r="1">
                        ${cells[0]}
                    </row>
                    <row r="2">
                        ${cells[1]}
                        ${cells[2]}
                    </row>
                    <row r="5">
                        ${cells[3]}
                        ${cells[4]}
                        ${cells[6]}
                        ${cells[8]}
                    </row>
                    <row r="6">
                        ${cells[5]}
                        ${cells[7]}
                    </row>
                </sheetData>
                <mergeCells count="4">
                    <mergeCell ref="C1:E1"/>
                    <mergeCell ref="C2:D2"/>
                    <mergeCell ref="C5:D5"/>
                    <mergeCell ref="C6:D6"/>
                </mergeCells>
            </worksheet>
        """.trimIndent()
    }

    private fun inlineCell(ref: String, value: String): String {
        return """<c r="$ref" t="inlineStr"><is><t>${escapeXml(value)}</t></is></c>"""
    }

    private fun escapeXml(value: String): String {
        return buildString(value.length) {
            value.forEach { char ->
                append(
                    when (char) {
                        '&' -> "&amp;"
                        '<' -> "&lt;"
                        '>' -> "&gt;"
                        '"' -> "&quot;"
                        '\'' -> "&apos;"
                        else -> char
                    }
                )
            }
        }
    }

    private fun ZipOutputStream.writeEntry(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
