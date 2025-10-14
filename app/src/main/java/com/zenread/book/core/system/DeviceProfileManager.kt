package com.zenread.book.core.system

import android.app.ActivityManager
import android.content.Context

class DeviceProfileManager(context: Context) {
    enum class RamLevel { LOW, MEDIUM, HIGH }

    val ramLevel: RamLevel
    val renderSemaphore: Int
    val cacheSize: Int
    val preloadDistance: Int

    init {
        ramLevel = detectRamLevel(context)
        renderSemaphore = createRenderSemaphore(ramLevel)
        cacheSize = determineCacheSize(ramLevel)
        preloadDistance = determinePreloadDistance(ramLevel)
    }

    private fun detectRamLevel(context: Context): RamLevel {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        return when {
            memoryInfo.totalMem < 2L * 1024 * 1024 * 1024 -> RamLevel.LOW
            memoryInfo.totalMem < 4L * 1024 * 1024 * 1024 -> RamLevel.MEDIUM
            else -> RamLevel.HIGH
        }
    }
    private fun createRenderSemaphore(level: RamLevel): Int {
        return when (level) {
            RamLevel.LOW -> 1
            RamLevel.MEDIUM -> 3
            RamLevel.HIGH -> 6
        }
    }

    private fun determineCacheSize(level: RamLevel): Int {
        return when (level) {
            RamLevel.LOW -> 12
            RamLevel.MEDIUM -> 30
            RamLevel.HIGH -> 60
        }
    }

    private fun determinePreloadDistance(level: RamLevel): Int {
        return when (level) {
            RamLevel.LOW -> 1
            RamLevel.MEDIUM -> 3
            RamLevel.HIGH -> 5
        }
    }
}