package ru.vsu.csschedule.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.vsu.csschedule.CSScheduleApplication
import ru.vsu.csschedule.R

@Composable
fun ScheduleDetailsRoute(
    onBack: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as CSScheduleApplication
    val viewModel: ScheduleDetailsViewModel = viewModel(
        factory = ScheduleDetailsViewModel.factory(
            scheduleRepository = application.scheduleRepository,
        )
    )
    val uiState by viewModel.uiState.collectAsState()

    ScheduleDetailsScreen(
        title = viewModel.title,
        uiState = uiState,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDetailsScreen(
    title: String,
    uiState: ScheduleDetailsUiState,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title.ifBlank { stringResource(R.string.schedule_details_title) }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.days.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.schedule_details_empty),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(uiState.days, key = { it.weekDayName }) { day ->
                    DayScheduleCard(day = day)
                }
            }
        }
    }
}

@Composable
private fun DayScheduleCard(day: DayScheduleUiItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = day.weekDayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            day.pairs.forEach { pair ->
                PairCard(pair = pair)
            }
        }
    }
}

@Composable
private fun PairCard(pair: PairScheduleUiItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = pair.timeRange,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            WeekTypeSection(
                title = stringResource(R.string.weektype_numerator_ru),
                lesson = pair.numeratorLesson,
            )
            WeekTypeSection(
                title = stringResource(R.string.weektype_denominator_ru),
                lesson = pair.denominatorLesson,
            )
        }
    }
}

@Composable
private fun WeekTypeSection(
    title: String,
    lesson: LessonUiItem?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (lesson == null) {
            Text(
                text = stringResource(R.string.schedule_window),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = lesson.subjectName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                PairMetaRow(
                    label = stringResource(R.string.group_label_ru),
                    value = lesson.groupName,
                )
                PairMetaRow(
                    label = stringResource(R.string.teacher_label_ru),
                    value = lesson.teacherName ?: stringResource(R.string.not_specified_ru),
                )
                PairMetaRow(
                    label = stringResource(R.string.auditorium_label_ru),
                    value = lesson.auditoriumName ?: stringResource(R.string.not_specified_ru),
                )
            }
        }
    }
}

@Composable
private fun PairMetaRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
