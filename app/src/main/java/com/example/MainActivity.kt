package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import coil.compose.AsyncImage
import com.example.data.model.FileCategory
import com.example.data.model.FileItem
import com.example.ui.navigation.DocumentScannerRoute
import com.example.ui.navigation.FileConverterRoute
import com.example.ui.navigation.FilesRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.ImageToolkitRoute
import com.example.ui.navigation.ImageToolsRoute
import com.example.ui.navigation.ImagesToPdfRoute
import com.example.ui.navigation.OnboardingRoute
import com.example.ui.navigation.PdfCompressRoute
import com.example.ui.navigation.PdfInfoRoute
import com.example.ui.navigation.PdfMergeRoute
import com.example.ui.navigation.PdfOrganizerRoute
import com.example.ui.navigation.PdfProtectRoute
import com.example.ui.navigation.PdfSplitRoute
import com.example.ui.navigation.PdfToImagesRoute
import com.example.ui.navigation.PdfToolsRoute
import com.example.ui.navigation.PdfViewerRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.SplashRoute
import com.example.ui.navigation.StorageOverviewRoute
import com.example.ui.navigation.ZipToolsRoute
import com.example.ui.screens.DocumentScannerScreen
import com.example.ui.screens.FileConverterScreen
import com.example.ui.screens.FileManagerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImageToolkitScreen
import com.example.ui.screens.ImageToolsHubScreen
import com.example.ui.screens.ImagesToPdfScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PdfCompressScreen
import com.example.ui.screens.PdfInfoScreen
import com.example.ui.screens.PdfMergeScreen
import com.example.ui.screens.PdfOrganizerScreen
import com.example.ui.screens.PdfProtectScreen
import com.example.ui.screens.PdfSplitScreen
import com.example.ui.screens.PdfToImagesScreen
import com.example.ui.screens.PdfToolsHubScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StorageOverviewScreen
import com.example.ui.screens.ZipToolsScreen
import com.example.ui.theme.FileFlowTheme
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val prefs by mainViewModel.preferences.collectAsStateWithLifecycle()

            FileFlowTheme(themeMode = prefs.themeMode) {
                FileFlowAppRoot(
                    viewModel = mainViewModel,
                    onboardingCompleted = prefs.onboardingCompleted
                )
            }
        }
    }
}

private data class TopLevelTab(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

@Composable
private fun FileFlowAppRoot(
    viewModel: MainViewModel,
    onboardingCompleted: Boolean
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val scope = rememberCoroutineScope()

    var quickPreviewFile by remember { mutableStateOf<FileItem?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { destUri ->
        if (destUri != null) {
            viewModel.exportPendingToUri(destUri) { res ->
                res.onSuccess {
                    Toast.makeText(context, "Saved to device storage", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(context, "Export failed: ${err.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            viewModel.setPendingExportFile(null)
        }
    }

    val requestExport: (FileItem) -> Unit = { item ->
        viewModel.setPendingExportFile(item)
        exportLauncher.launch(item.name)
    }

    val openFileSmart: (FileItem) -> Unit = { item ->
        viewModel.markAccessed(item)
        when {
            item.isExternalSaf -> viewModel.openExternal(item)
            item.category == FileCategory.PDF -> navController.navigate(PdfViewerRoute(item.path))
            item.category == FileCategory.IMAGE -> quickPreviewFile = item
            item.extension in setOf("txt", "md", "json", "xml", "csv", "html", "htm") -> quickPreviewFile = item
            item.extension == "zip" -> navController.navigate(ZipToolsRoute("extract"))
            else -> viewModel.openExternal(item)
        }
    }

    val tabs = remember {
        listOf(
            TopLevelTab(HomeRoute, "Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
            TopLevelTab(PdfToolsRoute, "PDF Tools", Icons.Filled.PictureAsPdf, Icons.Outlined.PictureAsPdf, "nav_pdf"),
            TopLevelTab(FilesRoute, "Files", Icons.Filled.Folder, Icons.Outlined.Folder, "nav_files"),
            TopLevelTab(ImageToolsRoute, "Images", Icons.Filled.Image, Icons.Outlined.Image, "nav_images"),
            TopLevelTab(SettingsRoute, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
        )
    }

    val isTopLevelRoute = currentDest?.let { dest ->
        dest.hasRoute<HomeRoute>() ||
            dest.hasRoute<PdfToolsRoute>() ||
            dest.hasRoute<FilesRoute>() ||
            dest.hasRoute<ImageToolsRoute>() ||
            dest.hasRoute<SettingsRoute>()
    } ?: false

    val navigateToTab: (Any) -> Unit = { target ->
        navController.navigate(target) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail && isTopLevelRoute) {
                NavigationRail(
                    modifier = Modifier
                        .fillMaxHeight()
                        .statusBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Spacer(Modifier.height(12.dp))
                    tabs.forEach { tab ->
                        val selected = when (tab.route) {
                            is HomeRoute -> currentDest?.hasRoute<HomeRoute>() == true
                            is PdfToolsRoute -> currentDest?.hasRoute<PdfToolsRoute>() == true
                            is FilesRoute -> currentDest?.hasRoute<FilesRoute>() == true
                            is ImageToolsRoute -> currentDest?.hasRoute<ImageToolsRoute>() == true
                            is SettingsRoute -> currentDest?.hasRoute<SettingsRoute>() == true
                            else -> false
                        }
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateToTab(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label, maxLines = 1) },
                            modifier = Modifier.testTag(tab.tag)
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (!useRail && isTopLevelRoute) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            tabs.forEach { tab ->
                                val selected = when (tab.route) {
                                    is HomeRoute -> currentDest?.hasRoute<HomeRoute>() == true
                                    is PdfToolsRoute -> currentDest?.hasRoute<PdfToolsRoute>() == true
                                    is FilesRoute -> currentDest?.hasRoute<FilesRoute>() == true
                                    is ImageToolsRoute -> currentDest?.hasRoute<ImageToolsRoute>() == true
                                    is SettingsRoute -> currentDest?.hasRoute<SettingsRoute>() == true
                                    else -> false
                                }
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label, maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isTopLevelRoute) innerPadding else androidx.compose.foundation.layout.PaddingValues(0.dp))
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = SplashRoute
                    ) {
                        composable<SplashRoute> {
                            SplashScreen(
                                onboardingCompleted = onboardingCompleted,
                                onNavigateToOnboarding = {
                                    navController.navigate(OnboardingRoute) {
                                        popUpTo(SplashRoute) { inclusive = true }
                                    }
                                },
                                onNavigateToHome = {
                                    navController.navigate(HomeRoute) {
                                        popUpTo(SplashRoute) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable<OnboardingRoute> {
                            OnboardingScreen(
                                onFinish = {
                                    viewModel.markOnboardingDone()
                                    navController.navigate(HomeRoute) {
                                        popUpTo(OnboardingRoute) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable<HomeRoute> {
                            HomeScreen(
                                viewModel = viewModel,
                                onOpenPdfToImages = { navController.navigate(PdfToImagesRoute) },
                                onOpenImagesToPdf = { navController.navigate(ImagesToPdfRoute) },
                                onOpenCompressPdf = { navController.navigate(PdfCompressRoute) },
                                onOpenMergePdf = { navController.navigate(PdfMergeRoute) },
                                onOpenSplitPdf = { navController.navigate(PdfSplitRoute) },
                                onOpenScanner = { navController.navigate(DocumentScannerRoute) },
                                onOpenCompressImage = { navController.navigate(ImageToolkitRoute("compress")) },
                                onOpenConverter = { navController.navigate(FileConverterRoute()) },
                                onOpenExtractZip = { navController.navigate(ZipToolsRoute("extract")) },
                                onOpenCreateZip = { navController.navigate(ZipToolsRoute("create")) },
                                onOpenPdfHub = { navigateToTab(PdfToolsRoute) },
                                onOpenImageHub = { navigateToTab(ImageToolsRoute) },
                                onOpenFilesTab = { navigateToTab(FilesRoute) },
                                onOpenStorage = { navController.navigate(StorageOverviewRoute) },
                                onOpenSettings = { navigateToTab(SettingsRoute) },
                                onOpenFile = openFileSmart
                            )
                        }

                        composable<PdfToolsRoute> {
                            PdfToolsHubScreen(
                                viewModel = viewModel,
                                onMerge = { navController.navigate(PdfMergeRoute) },
                                onSplit = { navController.navigate(PdfSplitRoute) },
                                onCompress = { navController.navigate(PdfCompressRoute) },
                                onImagesToPdf = { navController.navigate(ImagesToPdfRoute) },
                                onPdfToImages = { navController.navigate(PdfToImagesRoute) },
                                onOrganizer = { navController.navigate(PdfOrganizerRoute) },
                                onOpenViewer = { item -> navController.navigate(PdfViewerRoute(item.path)) },
                                onProtect = { navController.navigate(PdfProtectRoute) },
                                onInfo = { navController.navigate(PdfInfoRoute()) },
                                onScanner = { navController.navigate(DocumentScannerRoute) }
                            )
                        }

                        composable<FilesRoute> {
                            FileManagerScreen(
                                viewModel = viewModel,
                                onOpenFile = openFileSmart,
                                onRequestExport = requestExport
                            )
                        }

                        composable<ImageToolsRoute> {
                            ImageToolsHubScreen(
                                onOpenStudio = { mode -> navController.navigate(ImageToolkitRoute(mode)) },
                                onOpenScanner = { navController.navigate(DocumentScannerRoute) }
                            )
                        }

                        composable<SettingsRoute> {
                            SettingsScreen(
                                viewModel = viewModel,
                                onOpenStorage = { navController.navigate(StorageOverviewRoute) }
                            )
                        }

                        // PDF Master Feature Screens
                        composable<PdfMergeRoute> {
                            PdfMergeScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenViewer = { item -> navController.navigate(PdfViewerRoute(item.path)) },
                                onRequestExport = requestExport
                            )
                        }

                        composable<PdfSplitRoute> {
                            PdfSplitScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenViewer = { item -> navController.navigate(PdfViewerRoute(item.path)) },
                                onRequestExport = requestExport
                            )
                        }

                        composable<PdfCompressRoute> {
                            PdfCompressScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenViewer = { item -> navController.navigate(PdfViewerRoute(item.path)) },
                                onRequestExport = requestExport
                            )
                        }

                        composable<ImagesToPdfRoute> {
                            ImagesToPdfScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenViewer = { item -> navController.navigate(PdfViewerRoute(item.path)) },
                                onRequestExport = requestExport
                            )
                        }

                        composable<PdfToImagesRoute> {
                            PdfToImagesScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onRequestExport = requestExport
                            )
                        }

                        composable<PdfOrganizerRoute> {
                            PdfOrganizerScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenViewer = { item -> navController.navigate(PdfViewerRoute(item.path)) },
                                onRequestExport = requestExport
                            )
                        }

                        composable<PdfViewerRoute> { entry ->
                            val route = entry.toRoute<PdfViewerRoute>()
                            PdfViewerScreen(
                                filePath = route.filePath,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenInfo = { path -> navController.navigate(PdfInfoRoute(path)) },
                                onRequestExport = requestExport
                            )
                        }

                        composable<PdfProtectRoute> {
                            PdfProtectScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenViewer = { item -> navController.navigate(PdfViewerRoute(item.path)) },
                                onRequestExport = requestExport
                            )
                        }

                        composable<PdfInfoRoute> { entry ->
                            val route = entry.toRoute<PdfInfoRoute>()
                            PdfInfoScreen(
                                initialFilePath = route.filePath,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // Converter, Image Studio, ZIP, Scanner, Storage
                        composable<FileConverterRoute> {
                            FileConverterScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenImagesToPdf = { navController.navigate(ImagesToPdfRoute) },
                                onOpenPdfToImages = { navController.navigate(PdfToImagesRoute) },
                                onOpenExtractZip = { navController.navigate(ZipToolsRoute("extract")) },
                                onOpenViewer = { item -> navController.navigate(PdfViewerRoute(item.path)) },
                                onRequestExport = requestExport
                            )
                        }

                        composable<ImageToolkitRoute> { entry ->
                            val route = entry.toRoute<ImageToolkitRoute>()
                            ImageToolkitScreen(
                                initialTool = route.initialTool,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onRequestExport = requestExport
                            )
                        }

                        composable<ZipToolsRoute> { entry ->
                            val route = entry.toRoute<ZipToolsRoute>()
                            ZipToolsScreen(
                                initialMode = route.initialMode,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onRequestExport = requestExport
                            )
                        }

                        composable<DocumentScannerRoute> {
                            DocumentScannerScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenViewer = { item -> navController.navigate(PdfViewerRoute(item.path)) },
                                onRequestExport = requestExport
                            )
                        }

                        composable<StorageOverviewRoute> {
                            StorageOverviewScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenFile = openFileSmart
                            )
                        }
                    }
                }
            }
        }
    }

    // Built-in Quick Preview Modal for Images and Text Files
    quickPreviewFile?.let { item ->
        AlertDialog(
            onDismissRequest = { quickPreviewFile = null },
            title = { Text(item.name, maxLines = 1) },
            text = {
                if (item.category == FileCategory.IMAGE) {
                    AsyncImage(
                        model = File(item.path),
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    val previewText = remember(item.path) {
                        runCatching { File(item.path).readText().take(4000) }.getOrDefault("Unable to read text preview.")
                    }
                    Column(
                        modifier = Modifier
                            .heightIn(max = 340.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(previewText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = item
                    quickPreviewFile = null
                    viewModel.shareFile(target)
                }) { Text("Share") }
            },
            dismissButton = {
                TextButton(onClick = { quickPreviewFile = null }) { Text("Close") }
            }
        )
    }
}
