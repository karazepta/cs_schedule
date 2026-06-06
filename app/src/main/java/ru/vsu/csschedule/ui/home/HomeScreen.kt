package ru.vsu.csschedule.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.vsu.csschedule.CSScheduleApplication
import ru.vsu.csschedule.R
import ru.vsu.csschedule.data.local.ImportSnapshotEntity
import ru.vsu.csschedule.data.local.ImportStatus
import ru.vsu.csschedule.data.local.ScheduleOwnerType
import ru.vsu.csschedule.data.repository.SavedScheduleListItem
import ru.vsu.csschedule.data.repository.ScheduleImportRepository
import ru.vsu.csschedule.data.repository.SearchableScheduleEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeRoute(
    onOpenSchedule: (ScheduleOwnerType, Long, String) -> Unit,
) {
    val application = LocalContext.current.applicationContext as CSScheduleApplication
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            scheduleRepository = application.scheduleRepository,
            scheduleImportRepository = application.scheduleImportRepository,
            themePreferencesRepository = application.themePreferencesRepository,
        )
    )
    val uiState by viewModel.uiState.collectAsState()

    HomeScreen(
        uiState = uiState,
        importStatus = viewModel.importStatus(uiState.importSnapshot),
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onAddScheduleClick = viewModel::onAddScheduleClick,
        onDismissTypePicker = viewModel::onDismissTypePicker,
        onTypeChosen = viewModel::onTypeChosen,
        onDismissEntityPicker = viewModel::onDismissEntityPicker,
        onEntityQueryChange = viewModel::onEntityQueryChange,
        onCourseSelected = viewModel::onCourseSelected,
        onEntityChosen = viewModel::onEntityChosen,
        onDeleteSavedSchedule = viewModel::onDeleteSavedSchedule,
        onRefreshImport = viewModel::refreshImport,
        onThemeChange = viewModel::onThemeChange,
        onOpenSchedule = onOpenSchedule,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    uiState: HomeUiState,
    importStatus: ImportStatus,
    onSearchQueryChange: (String) -> Unit,
    onAddScheduleClick: () -> Unit,
    onDismissTypePicker: () -> Unit,
    onTypeChosen: (ScheduleOwnerType) -> Unit,
    onDismissEntityPicker: () -> Unit,
    onEntityQueryChange: (String) -> Unit,
    onCourseSelected: (Int) -> Unit,
    onEntityChosen: (SearchableScheduleEntity) -> Unit,
    onDeleteSavedSchedule: (Long) -> Unit,
    onRefreshImport: () -> Unit,
    onThemeChange: (Boolean) -> Unit,
    onOpenSchedule: (ScheduleOwnerType, Long, String) -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var drawerShowsDataSource by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (drawerShowsDataSource) {
                        TextButton(
                            onClick = { drawerShowsDataSource = false },
                        ) {
                            Text(stringResource(R.string.menu_back))
                        }
                        ImportSummaryCard(
                            snapshot = uiState.importSnapshot,
                            importStatus = importStatus,
                            showRefreshButton = true,
                            onRefresh = onRefreshImport,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.menu_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )

                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_data_source)) },
                            selected = false,
                            icon = {
                                ScheduleTypeIcon(
                                    iconRes = R.drawable.ic_sync,
                                    size = 24.dp,
                                )
                            },
                            onClick = { drawerShowsDataSource = true },
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isDarkTheme) {
                                            Icons.Outlined.DarkMode
                                        } else {
                                            Icons.Outlined.LightMode
                                        },
                                        contentDescription = null,
                                    )
                                    Column {
                                        Text(
                                            text = stringResource(R.string.dark_theme_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = stringResource(R.string.dark_theme_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Switch(
                                    checked = uiState.isDarkTheme,
                                    onCheckedChange = onThemeChange,
                                )
                            }
                        }
                    }
                }
            }
        },
    ) {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(onClick = onAddScheduleClick) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.add_schedule))
                }
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch { drawerState.open() }
                                }
                            ) {
                                Icon(
                                    Icons.Outlined.Menu,
                                    contentDescription = stringResource(R.string.open_menu),
                                )
                            }
                        }

                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Outlined.Search, contentDescription = null)
                            },
                            placeholder = {
                                Text(stringResource(R.string.search_hint))
                            },
                            shape = RoundedCornerShape(28.dp),
                            singleLine = true,
                        )
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.saved_schedules_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (uiState.savedSchedules.isEmpty()) {
                    item {
                        EmptyStateCard()
                    }
                } else {
                    items(uiState.savedSchedules, key = { it.id }) { item ->
                        SavedScheduleCard(
                            item = item,
                            semesterName = uiState.semesterName,
                            onOpen = { onOpenSchedule(item.type, item.referenceId, item.title) },
                            onDelete = { onDeleteSavedSchedule(item.id) },
                        )
                    }
                }
            }
        }
    }

    if (uiState.isTypePickerVisible) {
        TypePickerDialog(
            onDismiss = onDismissTypePicker,
            onTypeChosen = onTypeChosen,
        )
    }

    if (uiState.isEntityPickerVisible) {
        EntityPickerDialog(
            selectedType = uiState.selectedType,
            query = uiState.entityQuery,
            entities = uiState.entities,
            availableCourses = uiState.availableCourses,
            selectedCourseNumber = uiState.selectedCourseNumber,
            onDismiss = onDismissEntityPicker,
            onQueryChange = onEntityQueryChange,
            onCourseSelected = onCourseSelected,
            onEntityChosen = onEntityChosen,
        )
    }
}

@Composable
private fun ImportSummaryCard(
    snapshot: ImportSnapshotEntity?,
    importStatus: ImportStatus,
    showRefreshButton: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.import_summary_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                AssistChip(
                    onClick = {},
                    label = { Text(importStatus.asText()) },
                )
            }
            snapshot?.message?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = "${stringResource(R.string.source_link)}: ${snapshot?.sheetUrl ?: ScheduleImportRepository.SOURCE_PAGE_URL}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "${stringResource(R.string.last_checked)}: ${snapshot?.lastCheckedAt.formatTimestamp()}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "${stringResource(R.string.last_imported)}: ${snapshot?.lastImportedAt.formatTimestamp()}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (showRefreshButton) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onRefresh,
                        enabled = importStatus != ImportStatus.REFRESHING,
                    ) {
                        if (importStatus == ImportStatus.REFRESHING) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ScheduleTypeIcon(
                                    iconRes = R.drawable.ic_sync,
                                    size = 20.dp,
                                )
                                Text(stringResource(R.string.refresh_data_source))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.no_saved_schedules),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.no_saved_schedules_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SavedScheduleCard(
    item: SavedScheduleListItem,
    semesterName: String?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScheduleTypeIcon(
                iconRes = item.type.iconRes(),
                size = 52.dp,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = item.type.label(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                semesterName?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = stringResource(R.string.saved_schedule_semester, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.delete_saved_schedule),
                )
            }
        }
    }
}

@Composable
private fun TypePickerDialog(
    onDismiss: () -> Unit,
    onTypeChosen: (ScheduleOwnerType) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.choose_schedule_type),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TypeOptionRow(
                        title = stringResource(R.string.schedule_type_group),
                        iconRes = R.drawable.ic_groups,
                        onClick = { onTypeChosen(ScheduleOwnerType.GROUP) },
                    )
                    TypeOptionRow(
                        title = stringResource(R.string.schedule_type_teacher),
                        iconRes = R.drawable.ic_teachers,
                        onClick = { onTypeChosen(ScheduleOwnerType.TEACHER) },
                    )
                    TypeOptionRow(
                        title = stringResource(R.string.schedule_type_auditorium),
                        iconRes = R.drawable.ic_classrooms,
                        onClick = { onTypeChosen(ScheduleOwnerType.AUDITORIUM) },
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.close))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleTypeIcon(
    @DrawableRes iconRes: Int,
    size: Dp = 52.dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun TypeOptionRow(
    title: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScheduleTypeIcon(iconRes = iconRes, size = 52.dp)
            Text(text = title, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun CourseSelectorRow(
    courses: List<Int>,
    selectedCourse: Int?,
    onCourseSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        courses.forEach { course ->
            val isSelected = course == selectedCourse
            val shape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f)
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        },
                        shape = shape,
                    )
                    .clickable { onCourseSelected(course) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.course_chip, course),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun EntityPickerDialog(
    selectedType: ScheduleOwnerType?,
    query: String,
    entities: List<SearchableScheduleEntity>,
    availableCourses: List<Int>,
    selectedCourseNumber: Int?,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCourseSelected: (Int) -> Unit,
    onEntityChosen: (SearchableScheduleEntity) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = buildString {
                        append(stringResource(R.string.pick_schedule_title))
                        selectedType?.let {
                            append(": ")
                            append(it.label())
                        }
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (selectedType == ScheduleOwnerType.GROUP && availableCourses.isNotEmpty()) {
                    CourseSelectorRow(
                        courses = availableCourses,
                        selectedCourse = selectedCourseNumber,
                        onCourseSelected = onCourseSelected,
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search_entity_hint)) },
                    singleLine = true,
                )
                if (selectedType == ScheduleOwnerType.GROUP && selectedCourseNumber == null) {
                    Text(
                        text = stringResource(R.string.no_entities_for_type),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else if (entities.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_entities_for_type),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(entities, key = { it.id }) { entity ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.large)
                                    .clickable { onEntityChosen(entity) },
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(
                                    text = entity.name,
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleOwnerType.label(): String = when (this) {
    ScheduleOwnerType.GROUP -> stringResource(R.string.schedule_type_group)
    ScheduleOwnerType.TEACHER -> stringResource(R.string.schedule_type_teacher)
    ScheduleOwnerType.AUDITORIUM -> stringResource(R.string.schedule_type_auditorium)
}

@DrawableRes
private fun ScheduleOwnerType.iconRes(): Int = when (this) {
    ScheduleOwnerType.GROUP -> R.drawable.ic_groups
    ScheduleOwnerType.TEACHER -> R.drawable.ic_teachers
    ScheduleOwnerType.AUDITORIUM -> R.drawable.ic_classrooms
}

@Composable
private fun ImportStatus.asText(): String = when (this) {
    ImportStatus.IDLE -> stringResource(R.string.import_status_idle)
    ImportStatus.REFRESHING -> stringResource(R.string.import_status_refreshing)
    ImportStatus.UNCHANGED -> stringResource(R.string.import_status_unchanged)
    ImportStatus.DOWNLOADED -> stringResource(R.string.import_status_downloaded)
    ImportStatus.MAPPED -> stringResource(R.string.import_status_mapped)
    ImportStatus.FAILED -> stringResource(R.string.import_status_failed)
}

private fun Long?.formatTimestamp(): String {
    if (this == null) return "n/a"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(this))
}
