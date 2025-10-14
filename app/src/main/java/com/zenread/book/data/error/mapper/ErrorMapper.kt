package com.zenread.book.data.error.mapper

import android.content.Context
import com.zenread.book.R
import com.zenread.book.data.error.ERROR_LOAD_FILE
import com.zenread.book.data.error.NUM_FAIL_ERROR
import com.zenread.book.domain.repository.ErrorMapperSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ErrorMapper @Inject constructor(
    @ApplicationContext val context: Context
) : ErrorMapperSource {

    override fun getErrorString(codeId: Int, vararg args: Any): String {
        val rawText = context.resources.getText(codeId).toString()
        val hasPlaceholder = rawText.contains("%")

        return if (hasPlaceholder && args.isNotEmpty()) {
            context.getString(codeId, *args)
        } else {
            context.getString(codeId)
        }
    }
    fun getErrorMessage(errorCode: Int, vararg args: Any): String {
        val stringResId = errorsMap[errorCode]
            ?: throw IllegalArgumentException("String for error code not found: $errorCode")

        return getErrorString(stringResId, *args)
    }

    override val errorsMap: Map<Int, Int>
        get() = mapOf(
            Pair(ERROR_LOAD_FILE, R.string.error_load_file),
            Pair(NUM_FAIL_ERROR, R.string.num_fail_file)
        )
}