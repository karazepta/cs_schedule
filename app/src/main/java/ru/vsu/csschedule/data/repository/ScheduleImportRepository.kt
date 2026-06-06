package ru.vsu.csschedule.data.repository

import android.content.Context
import kotlinx.coroutines.flow.Flow
import ru.vsu.csschedule.data.importing.ScheduleSourceClient
import ru.vsu.csschedule.data.importing.SheetDownloadSnapshot
import ru.vsu.csschedule.data.importing.SheetManualMapper
import ru.vsu.csschedule.data.importing.SheetMappingResult
import ru.vsu.csschedule.data.local.ImportSnapshotEntity
import ru.vsu.csschedule.data.local.ImportStatus
import ru.vsu.csschedule.data.local.ScheduleDao
import java.io.File

class ScheduleImportRepository(
    private val scheduleDao: ScheduleDao,
    private val scheduleSourceClient: ScheduleSourceClient,
    private val sheetManualMapper: SheetManualMapper,
    private val context: Context,
) {
    fun observeSnapshot(): Flow<ImportSnapshotEntity?> = scheduleDao.observeImportSnapshot()

    suspend fun refreshIfNeeded() {
        val now = System.currentTimeMillis()
        val previous = scheduleDao.getImportSnapshot()
        val sourcePageUrl = SOURCE_PAGE_URL

        scheduleDao.upsertImportSnapshot(
            ImportSnapshotEntity(
                sourcePageUrl = sourcePageUrl,
                sheetUrl = previous?.sheetUrl,
                rawFilePath = previous?.rawFilePath,
                rawHash = previous?.rawHash,
                status = ImportStatus.REFRESHING,
                message = "Refreshing source page and checking the latest sheet",
                lastCheckedAt = now,
                lastImportedAt = previous?.lastImportedAt,
            )
        )

        runCatching {
            val sheetUrl = scheduleSourceClient.resolveSheetUrl(sourcePageUrl)
            val downloadedSheet = scheduleSourceClient.downloadSheet(sheetUrl)
            val contentHash = scheduleSourceClient.calculateSha256(downloadedSheet.bytes)
            val targetFile = File(
                context.filesDir,
                "imports/latest-schedule.${downloadedSheet.extension}"
            )
            scheduleSourceClient.persistDownloadedSheet(targetFile, downloadedSheet)
            val snapshot = SheetDownloadSnapshot(
                sourcePageUrl = sourcePageUrl,
                sheetUrl = sheetUrl,
                localFilePath = targetFile.absolutePath,
                contentHash = contentHash,
                downloadedAt = now,
            )

            if (previous?.rawHash == contentHash) {
                val result = sheetManualMapper.map(snapshot)
                applyMappingResult(
                    result = result,
                    sourcePageUrl = sourcePageUrl,
                    sheetUrl = sheetUrl,
                    targetFile = targetFile,
                    contentHash = contentHash,
                    now = now,
                    previous = previous,
                    unchangedMessage = "Источник не изменился, локальная база переиндексирована текущим mapper-ом",
                )
                return
            }

            when (val result = sheetManualMapper.map(snapshot)) {
                else -> applyMappingResult(
                    result = result,
                    sourcePageUrl = sourcePageUrl,
                    sheetUrl = sheetUrl,
                    targetFile = targetFile,
                    contentHash = contentHash,
                    now = now,
                    previous = previous,
                )
            }
        }.onFailure { error ->
            scheduleDao.upsertImportSnapshot(
                ImportSnapshotEntity(
                    sourcePageUrl = sourcePageUrl,
                    sheetUrl = previous?.sheetUrl,
                    rawFilePath = previous?.rawFilePath,
                    rawHash = previous?.rawHash,
                    status = ImportStatus.FAILED,
                    message = error.message ?: error.toString(),
                    lastCheckedAt = now,
                    lastImportedAt = previous?.lastImportedAt,
                )
            )
        }
    }

    companion object {
        const val SOURCE_PAGE_URL = "https://www.cs.vsu.ru/rasp/"
    }

    private suspend fun applyMappingResult(
        result: SheetMappingResult,
        sourcePageUrl: String,
        sheetUrl: String,
        targetFile: File,
        contentHash: String,
        now: Long,
        previous: ImportSnapshotEntity?,
        unchangedMessage: String? = null,
    ) {
        when (result) {
            is SheetMappingResult.Success -> {
                scheduleDao.replaceImportedData(result.data)
                scheduleDao.upsertImportSnapshot(
                    ImportSnapshotEntity(
                        sourcePageUrl = sourcePageUrl,
                        sheetUrl = sheetUrl,
                        rawFilePath = targetFile.absolutePath,
                        rawHash = contentHash,
                        status = if (unchangedMessage == null) ImportStatus.MAPPED else ImportStatus.UNCHANGED,
                        message = unchangedMessage ?: result.summary,
                        lastCheckedAt = now,
                        lastImportedAt = now,
                    )
                )
            }

            is SheetMappingResult.NotConfigured -> {
                scheduleDao.upsertImportSnapshot(
                    ImportSnapshotEntity(
                        sourcePageUrl = sourcePageUrl,
                        sheetUrl = sheetUrl,
                        rawFilePath = targetFile.absolutePath,
                        rawHash = contentHash,
                        status = ImportStatus.DOWNLOADED,
                        message = result.summary,
                        lastCheckedAt = now,
                        lastImportedAt = previous?.lastImportedAt,
                    )
                )
            }

            is SheetMappingResult.Failure -> {
                scheduleDao.upsertImportSnapshot(
                    ImportSnapshotEntity(
                        sourcePageUrl = sourcePageUrl,
                        sheetUrl = sheetUrl,
                        rawFilePath = targetFile.absolutePath,
                        rawHash = contentHash,
                        status = ImportStatus.FAILED,
                        message = result.summary,
                        lastCheckedAt = now,
                        lastImportedAt = previous?.lastImportedAt,
                    )
                )
            }
        }
    }
}
