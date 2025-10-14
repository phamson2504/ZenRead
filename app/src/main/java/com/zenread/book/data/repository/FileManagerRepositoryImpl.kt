package com.zenread.book.data.repository

import android.app.Application
import android.provider.DocumentsContract
import com.zenread.book.data.file.CachedFileWrapper
import com.zenread.book.domain.model.CachedFileBuilder
import com.zenread.book.domain.repository.FileManagerRepository
import com.zenread.book.domain.service.GetBooksService
import com.zenread.book.core.utils.Constants
import java.util.UUID
import javax.inject.Inject

class FileManagerRepositoryImpl @Inject constructor(
    private val application: Application,
    private val booksService: GetBooksService
) : FileManagerRepository {

    override suspend fun getListFiles(query: String): List<CachedFileWrapper> {
        val currentPaths = booksService.getBooks().map { it.filePath }

        fun CachedFileWrapper.isValid(): Boolean {
            if (!Constants.fileTypeAccess.any { ext ->
                    name.endsWith(ext, ignoreCase = true)
                }) {
                return false
            }
            if (currentPaths.any { currentPath ->
                    currentPath.equals(path, ignoreCase = true)
                }) {
                return false
            }
            return true
        }

        fun CachedFileWrapper.openFile(): List<CachedFileWrapper> {
            val cachedFileWrappers = mutableListOf<CachedFileWrapper>()
            this.listFiles().forEach { file ->
                if (file.isDirectory) {
                    cachedFileWrappers.addAll(file.openFile())
                } else {
                    cachedFileWrappers.add(file)
                }
            }
            return cachedFileWrappers
        }

        fun CachedFileWrapper.getFilesFromStorage(): List<CachedFileWrapper> {
            val accessedFiles = mutableListOf<CachedFileWrapper>()
            if (this.isValid()) {
                accessedFiles.add(this)
            } else {
                val allFiles = this.openFile()
                for (file in allFiles) {
                    if (!file.isValid()) continue
                    accessedFiles.add(file)
                }
            }
            return accessedFiles
        }

        return try {
            val getAllFile = getAllFile()
            val files = mutableListOf<CachedFileWrapper>()
            for (file in getAllFile) {
                files.addAll(file.getFilesFromStorage())
            }
            files
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getAllFile(): List<CachedFileWrapper> {
        val allFiles =
            application.contentResolver.persistedUriPermissions.mapNotNull { permission ->
                try {
                    val uri = permission.uri
                    val documentUri = when {
                        DocumentsContract.isTreeUri(uri) -> {
                            val docId = DocumentsContract.getTreeDocumentId(uri)
                            DocumentsContract.buildDocumentUriUsingTree(uri, docId)
                        }

                        DocumentsContract.isDocumentUri(application, uri) -> uri
                        else -> uri
                    }

                    CachedFileWrapper(
                        application,
                        documentUri,
                        CachedFileBuilder(
                            name = UUID.randomUUID().toString(),
                            size = 0,
                            lastModified = 0
                        )
                    ).takeIf { it.path.isNotBlank() }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

        // Filter out files in existing parent directories
        return allFiles.filter { file ->
            allFiles.none { other ->
                other !== file && file.path.startsWith(other.path, ignoreCase = true)
            }
        }
    }

}