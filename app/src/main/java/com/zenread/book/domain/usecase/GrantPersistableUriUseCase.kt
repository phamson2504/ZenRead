package com.zenread.book.domain.usecase

import android.net.Uri
import com.zenread.book.domain.repository.PermissionRepository
import javax.inject.Inject

class GrantPersistableUriUseCase @Inject constructor(
    private val permissionRepository: PermissionRepository
) {
     operator fun invoke(uri: Uri) {
        permissionRepository.grantPersistableUriPermission(uri)
    }
}