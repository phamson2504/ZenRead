package com.zenread.book.data.repository

import android.app.Application
import android.content.Intent
import android.net.Uri
import com.zenread.book.domain.repository.PermissionRepository
import javax.inject.Inject

class PermissionRepositoryImpl @Inject constructor(
    private val application: Application
) : PermissionRepository {
    override fun grantPersistableUriPermission(uri: Uri) {
        try {
            application.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun releasePersistableUriPermission(uri: Uri) {
        try {
            application.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}