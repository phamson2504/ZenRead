package com.zenread.book.domain.model

import android.content.pm.ResolveInfo

data class TranslationResult(
    val apps: List<ResolveInfo>,
    val browserUrl: String,
    val text: String
)