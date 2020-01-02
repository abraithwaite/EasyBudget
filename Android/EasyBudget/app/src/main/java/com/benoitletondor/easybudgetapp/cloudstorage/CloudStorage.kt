package com.benoitletondor.easybudgetapp.cloudstorage

import java.io.File
import java.util.*

interface CloudStorage {
    suspend fun uploadFile(file: File, path: String)
    suspend fun uploadFile(file: File, path: String, progressListener: ((Double) -> Unit)?)
    suspend fun getFileMetaData(path: String): FileMetaData
    suspend fun downloadFile(path: String, toFile: File)
}

data class FileMetaData(val path: String, val lastUpdateDate: Date)