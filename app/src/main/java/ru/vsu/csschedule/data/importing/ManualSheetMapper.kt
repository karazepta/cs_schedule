package ru.vsu.csschedule.data.importing

import ru.vsu.csschedule.data.local.ImportedScheduleData

data class SheetDownloadSnapshot(
    val sourcePageUrl: String,
    val sheetUrl: String,
    val localFilePath: String,
    val contentHash: String,
    val downloadedAt: Long,
)

sealed interface SheetMappingResult {
    data class Success(
        val data: ImportedScheduleData,
        val summary: String,
    ) : SheetMappingResult

    data class NotConfigured(
        val summary: String,
    ) : SheetMappingResult

    data class Failure(
        val summary: String,
    ) : SheetMappingResult
}

interface SheetManualMapper {
    suspend fun map(snapshot: SheetDownloadSnapshot): SheetMappingResult
}

class PlaceholderSheetManualMapper : SheetManualMapper {
    override suspend fun map(snapshot: SheetDownloadSnapshot): SheetMappingResult {
        return SheetMappingResult.NotConfigured(
            summary = "Downloaded `${snapshot.localFilePath}`. Fill ManualSheetMapper with your cell mapping."
        )
    }
}
