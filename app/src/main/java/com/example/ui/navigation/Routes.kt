package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object SplashRoute

@Serializable
object OnboardingRoute

// 5 Primary Bottom Navigation Destinations
@Serializable
object HomeRoute

@Serializable
object PdfToolsRoute

@Serializable
object FilesRoute

@Serializable
object ImageToolsRoute

@Serializable
object SettingsRoute

// PDF Master Tool Routes
@Serializable
object PdfMergeRoute

@Serializable
object PdfSplitRoute

@Serializable
object PdfCompressRoute

@Serializable
object ImagesToPdfRoute

@Serializable
object PdfToImagesRoute

@Serializable
object PdfOrganizerRoute

@Serializable
data class PdfViewerRoute(val filePath: String)

@Serializable
object PdfProtectRoute

@Serializable
data class PdfInfoRoute(val filePath: String? = null)

// Converter, Image Toolkit, Archive, Scanner, Storage
@Serializable
data class FileConverterRoute(val initialMode: String? = null)

@Serializable
data class ImageToolkitRoute(val initialTool: String? = null)

@Serializable
data class ZipToolsRoute(val initialMode: String? = null)

@Serializable
object DocumentScannerRoute

@Serializable
object StorageOverviewRoute
