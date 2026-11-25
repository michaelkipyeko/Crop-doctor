package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.data.ai.CropDiagnosisAiRepository
import com.example.data.ai.FirebaseAiCropDiagnosisRepository
import com.example.data.local.CropDatabase
import com.example.data.local.CropHistoryRepository
import com.example.ui.screens.DiagnosisDetailScreen
import com.example.ui.screens.FieldHubScreen
import com.example.ui.screens.FieldLogScreen
import com.example.ui.screens.LiveScannerScreen
import com.example.ui.viewmodel.CropDoctorViewModel
import com.example.ui.viewmodel.DiagnosisDetailViewModel

object CropDoctorRoutes {
    const val HOME = "home"
    const val SCANNER = "scanner"
    const val HISTORY = "history"
    const val DETAIL = "diagnosis/{recordId}"

    fun detail(recordId: Long): String = "diagnosis/$recordId"
}

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topLevelDestinations = listOf(
    TopLevelDestination(
        route = CropDoctorRoutes.HOME,
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Rounded.Eco,
        unselectedIcon = Icons.Outlined.Eco,
        testTag = "nav_home_tab"
    ),
    TopLevelDestination(
        route = CropDoctorRoutes.SCANNER,
        labelRes = R.string.nav_scanner,
        selectedIcon = Icons.Rounded.CameraAlt,
        unselectedIcon = Icons.Outlined.CameraAlt,
        testTag = "nav_scanner_tab"
    ),
    TopLevelDestination(
        route = CropDoctorRoutes.HISTORY,
        labelRes = R.string.nav_history,
        selectedIcon = Icons.Rounded.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_history_tab"
    )
)

@Composable
fun CropDoctorApp() {
    val context = LocalContext.current
    val appContext = context.applicationContext

    val aiRepository: CropDiagnosisAiRepository = remember {
        FirebaseAiCropDiagnosisRepository()
    }
    val historyRepository = remember(appContext) {
        val db = CropDatabase.getInstance(appContext)
        CropHistoryRepository(db.diagnosisDao(), appContext)
    }

    val mainViewModel: CropDoctorViewModel = viewModel(
        factory = CropDoctorViewModel.Factory(aiRepository, historyRepository)
    )

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isTopLevelRoute = topLevelDestinations.any { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }

    val openDiagnosisDetail: (Long) -> Unit = { recordId ->
        navController.navigate(CropDoctorRoutes.detail(recordId))
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            mainViewModel.diagnosePickedUri(context, uri, onSaved = openDiagnosisDetail)
        }
    }

    val launchPhotoPicker = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val navigateTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val records by mainViewModel.historyRecords.collectAsStateWithLifecycle()
    val scannerUiState by mainViewModel.scannerUiState.collectAsStateWithLifecycle()
    val livePulseState by mainViewModel.livePulseState.collectAsStateWithLifecycle()
    val selectedCropHint by mainViewModel.selectedCropHint.collectAsStateWithLifecycle()
    val livePulseEnabled by mainViewModel.livePulseEnabled.collectAsStateWithLifecycle()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (isTopLevelRoute && !useNavRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topLevelDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevelRoute && useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.statusBarsPadding()
                    ) {
                        topLevelDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = CropDoctorRoutes.HOME
                    ) {
                        composable(CropDoctorRoutes.HOME) {
                            FieldHubScreen(
                                records = records,
                                sampleSpecimens = mainViewModel.sampleSpecimens,
                                diseaseGuides = mainViewModel.diseaseGuides,
                                isAnalyzing = scannerUiState is com.example.ui.viewmodel.ScannerUiState.Analyzing,
                                scannerError = (scannerUiState as? com.example.ui.viewmodel.ScannerUiState.Error)?.message,
                                onDismissError = mainViewModel::clearScannerError,
                                onStartLiveScan = { navigateTopLevel(CropDoctorRoutes.SCANNER) },
                                onPickPhoto = launchPhotoPicker,
                                onInspectSpecimen = { specimen ->
                                    mainViewModel.diagnoseSampleSpecimen(
                                        context = context,
                                        specimen = specimen,
                                        onSaved = openDiagnosisDetail
                                    )
                                },
                                onOpenHistory = { navigateTopLevel(CropDoctorRoutes.HISTORY) }
                            )
                        }

                        composable(CropDoctorRoutes.SCANNER) {
                            LiveScannerScreen(
                                scannerUiState = scannerUiState,
                                livePulseState = livePulseState,
                                cropHints = mainViewModel.cropHints,
                                selectedCropHint = selectedCropHint,
                                livePulseEnabled = livePulseEnabled,
                                sampleSpecimens = mainViewModel.sampleSpecimens,
                                onSelectCropHint = mainViewModel::selectCropHint,
                                onToggleLivePulse = mainViewModel::toggleLivePulse,
                                onFrameBitmap = mainViewModel::onLiveCameraFrame,
                                onCaptureBitmap = { bmp ->
                                    mainViewModel.diagnoseCapturedBitmap(bmp, onSaved = openDiagnosisDetail)
                                },
                                onPickPhoto = launchPhotoPicker,
                                onInspectSpecimen = { specimen ->
                                    mainViewModel.diagnoseSampleSpecimen(
                                        context = context,
                                        specimen = specimen,
                                        onSaved = openDiagnosisDetail
                                    )
                                },
                                onDismissError = mainViewModel::clearScannerError,
                                modifier = Modifier.statusBarsPadding()
                            )
                        }

                        composable(CropDoctorRoutes.HISTORY) {
                            FieldLogScreen(
                                records = records,
                                onSelectRecord = openDiagnosisDetail,
                                onStartScan = { navigateTopLevel(CropDoctorRoutes.SCANNER) },
                                modifier = Modifier.statusBarsPadding()
                            )
                        }

                        composable(
                            route = CropDoctorRoutes.DETAIL,
                            arguments = listOf(
                                navArgument("recordId") { type = NavType.LongType }
                            )
                        ) { backStackEntry ->
                            val recordId = backStackEntry.arguments?.getLong("recordId") ?: 0L
                            val detailViewModel: DiagnosisDetailViewModel = viewModel(
                                key = "detail_$recordId",
                                factory = DiagnosisDetailViewModel.Factory(
                                    recordId = recordId,
                                    aiRepository = aiRepository,
                                    historyRepository = historyRepository
                                )
                            )
                            val record by detailViewModel.record.collectAsStateWithLifecycle()
                            val messages by detailViewModel.messages.collectAsStateWithLifecycle()
                            val isAsking by detailViewModel.isAsking.collectAsStateWithLifecycle()
                            val agronomistError by detailViewModel.agronomistError.collectAsStateWithLifecycle()

                            DiagnosisDetailScreen(
                                record = record,
                                messages = messages,
                                isAskingAgronomist = isAsking,
                                agronomistError = agronomistError,
                                onBack = { navController.popBackStack() },
                                onToggleStep = detailViewModel::toggleStep,
                                onToggleResolved = detailViewModel::toggleResolved,
                                onDelete = {
                                    detailViewModel.deleteRecord {
                                        navController.popBackStack()
                                    }
                                },
                                onAskQuestion = detailViewModel::askAgronomist
                            )
                        }
                    }
                }
            }
        }
    }
}
