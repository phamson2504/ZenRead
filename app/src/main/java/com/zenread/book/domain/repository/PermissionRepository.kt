package com.zenread.book.domain.repository

import android.net.Uri

interface PermissionRepository {
    fun grantPersistableUriPermission(uri: Uri)
    fun releasePersistableUriPermission(uri: Uri)
}