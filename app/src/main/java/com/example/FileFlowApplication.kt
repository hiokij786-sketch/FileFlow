package com.example

import android.app.Application
import com.example.data.local.FileFlowDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.FileWorkspaceRepository
import com.example.engine.PdfEngine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class FileFlowApplication : Application() {

    lateinit var database: FileFlowDatabase
        private set
    lateinit var workspaceRepository: FileWorkspaceRepository
        private set
    lateinit var preferencesRepository: UserPreferencesRepository
        private set
    lateinit var pdfEngine: PdfEngine
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        database = FileFlowDatabase.getInstance(this)
        workspaceRepository = FileWorkspaceRepository(this, database.fileRecordDao())
        preferencesRepository = UserPreferencesRepository(this)
        pdfEngine = PdfEngine(this)
    }
}
