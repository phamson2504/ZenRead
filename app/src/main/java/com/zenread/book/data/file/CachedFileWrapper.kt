package com.zenread.book.data.file

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.getAbsolutePath
import com.zenread.book.domain.model.CachedFileBuilder
import java.io.File
import java.io.InputStream
import java.util.UUID

class CachedFileWrapper(
    private val context: Context,
    val uri: Uri,
    private val builder: CachedFileBuilder? = null
) {
    companion object {
        private const val COLUMN_NAME = DocumentsContract.Document.COLUMN_DISPLAY_NAME
        private const val COLUMN_SIZE = DocumentsContract.Document.COLUMN_SIZE
        private const val COLUMN_LAST_MODIFIED = DocumentsContract.Document.COLUMN_LAST_MODIFIED
        private const val COLUMN_MIME_TYPE = DocumentsContract.Document.COLUMN_MIME_TYPE
        private const val COLUMN_DOCUMENT_ID = DocumentsContract.Document.COLUMN_DOCUMENT_ID
    }

    data class BookFileInfo(
        val name: String,
        val size: Long,
        val lastModified: Long,
        val isDirectory: Boolean
    )

    private val bookFileInfo by lazy { getParamBookFileInfo() }
    val path: String get() = builder?.path ?: getFilePath()

    val name: String get() = builder?.name ?: bookFileInfo.name
    val size: Long get() = builder?.size ?: bookFileInfo.size
    val lastModified: Long get() = builder?.lastModified ?: bookFileInfo.lastModified
    val isDirectory: Boolean get() = builder?.isDirectory ?: bookFileInfo.isDirectory

    private fun canAccess(): Boolean {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use {} != null
        } catch (_: Exception) {
            false
        }
    }

    fun listFiles(): List<CachedFileWrapper> {
        if (!isDirectory || !canAccess()) return emptyList()

        val listFileResults = mutableListOf<CachedFileWrapper>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            uri,
            DocumentsContract.getDocumentId(uri)
        )
        context.contentResolver.query(
            childrenUri,
            null, null, null, null
        )?.use { cursor ->
            try {
                val nameIndex = cursor.getColumnIndexOrThrow(COLUMN_NAME)
                val documentIdIndex = cursor.getColumnIndexOrThrow(COLUMN_DOCUMENT_ID)
                val sizeIndex = cursor.getColumnIndexOrThrow(COLUMN_SIZE)
                val lastModifiedIndex = cursor.getColumnIndexOrThrow(COLUMN_LAST_MODIFIED)
                val mimeTypeIndex = cursor.getColumnIndexOrThrow(COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val childName = cursor.getString(nameIndex)
                    //chill uri
                    val childId = cursor.getString(documentIdIndex)
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(uri, childId)
                    //chill size
                    val childSize = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                    //chillModified
                    val childLastModified =
                        if (lastModifiedIndex >= 0) cursor.getLong(lastModifiedIndex) else 0L
                    //childIsDirectory
                    val mimeType = if (mimeTypeIndex >= 0) cursor.getString(mimeTypeIndex) else null
                    val childIsDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR


                    val cachedFileWrapper = CachedFileWrapper(
                        context,
                        childUri,
                        CachedFileBuilder(
                            name = childName,
                            path = "$path/$childName",
                            size = childSize,
                            lastModified = childLastModified,
                            isDirectory = childIsDirectory
                        )
                    )

                    listFileResults.add(cachedFileWrapper)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return listFileResults
    }

    private fun getParamBookFileInfo(): BookFileInfo {
        val projection = mutableListOf<String>().apply {
            if (builder?.name == null) add(COLUMN_NAME)
            if (builder?.size == null) add(COLUMN_SIZE)
            if (builder?.lastModified == null) add(COLUMN_LAST_MODIFIED)
            if (builder?.isDirectory == null) add(COLUMN_MIME_TYPE)
        }

        if (projection.isEmpty() && builder != null) {
            return BookFileInfo(
                name = builder.name!!,
                size = builder.size!!,
                lastModified = builder.lastModified!!,
                isDirectory = builder.isDirectory!!
            )
        }

        context.contentResolver.query(
            uri,
            projection.toTypedArray(),
            null, null, null
        )?.use { cursor ->
            try {
                if (cursor.moveToFirst()) {
                    var nameValue = builder?.name
                    var sizeValue = builder?.size
                    var lastModifiedValue = builder?.lastModified
                    var isDirectoryValue = builder?.isDirectory

                    projection.forEach { column ->
                        when (column) {
                            COLUMN_NAME -> if (nameValue == null) {
                                nameValue = cursor.getString(cursor.getColumnIndexOrThrow(column))
                            }

                            COLUMN_SIZE -> if (sizeValue == null) {
                                sizeValue = cursor.getLong(cursor.getColumnIndexOrThrow(column))
                            }

                            COLUMN_LAST_MODIFIED -> if (lastModifiedValue == null) {
                                lastModifiedValue =
                                    cursor.getLong(cursor.getColumnIndexOrThrow(column))
                            }

                            COLUMN_MIME_TYPE -> if (isDirectoryValue == null) {
                                isDirectoryValue =
                                    when (cursor.getString(cursor.getColumnIndexOrThrow(column))) {
                                        DocumentsContract.Document.MIME_TYPE_DIR -> true
                                        null -> null
                                        else -> false
                                    }
                            }
                        }
                    }

                    return BookFileInfo(
                        name = nameValue ?: "unknown_${UUID.randomUUID()}",
                        size = sizeValue ?: 0,
                        lastModified = lastModifiedValue ?: 0,
                        isDirectory = isDirectoryValue ?: false
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return BookFileInfo(
            name = "unknown_${UUID.randomUUID()}",
            size = 0,
            lastModified = 0,
            isDirectory = false
        )
    }

    fun openInputStream(): InputStream? {
        return try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getFilePath(): String {
        val tempFile = DocumentFileCompat.fromUri(context, uri)
        return tempFile?.getAbsolutePath(context)?.trimEnd('/') ?: ""
    }
}


