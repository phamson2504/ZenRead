package com.zenread.book.data.parser.pdf

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.zenread.book.domain.model.WordInfo
import java.io.IOException
import javax.inject.Inject
import kotlin.math.abs
import kotlin.text.toFloat

class PdfTextParserImpl @Inject constructor() : PdfTextParser {

    override fun getWordClusterTouch(
        wordList: List<WordInfo>,
        pdfTouchRect: RectF
    ): List<WordInfo> {

        val center = wordList.firstOrNull {
            RectF.intersects(it.rect, pdfTouchRect)
        } ?: return emptyList()

        val sameLine = wordList.filter { word ->
            word.lineYKey == center.lineYKey && word.columnIndex == center.columnIndex && word.pageIndex == center.pageIndex
        }.sortedBy { it.rect.left }

        // Calculate the average distance between words in a line
        val gaps = sameLine.zipWithNext()
            .map { (w1, w2) -> w2.rect.left - w1.rect.right }
            .filter { it >= 0 }

        val averageGap = if (gaps.isNotEmpty()) gaps.average().toFloat() else 0f

        // Group phrases from center to left & right
        val result = mutableListOf<WordInfo>()

        val centerIndex = sameLine.indexOf(center)
        if (centerIndex == -1) return listOf(center) // fallback

        // From center to left
        for (i in centerIndex downTo 1) {
            val prev = sameLine[i - 1]
            val curr = sameLine[i]
            val gap = curr.rect.left - prev.rect.right
            if (gap <= averageGap  && !prev.word.all { it.isWhitespace() }) {
                result.add(0, prev)
            } else break
        }

        result.add(center)

        // From center to right
        for (i in centerIndex until sameLine.size - 1) {
            val curr = sameLine[i]
            val next = sameLine[i + 1]
            val gap = next.rect.left - curr.rect.right
            if (gap <= averageGap && !next.word.all { it.isWhitespace() }) {
                result.add(next)
            } else break
        }

        return result
    }

    override fun getWordsInPage(pdd: PDDocument, pageIndex: Int): List<WordInfo> {
        try {
            val stripper = CustomTextStripper(pageIndex)
            stripper.wordInfoList.clear()
            stripper.startPage = pageIndex + 1
            stripper.endPage = pageIndex + 1
            stripper.getText(pdd)
            return stripper.wordInfoList
        } catch (e: IOException) {
            Log.e("PdfTextHighlighter", "Failed to extract words on page $pageIndex", e)
            return emptyList()
        }
    }

    override fun textParserPointer(
        words: List<WordInfo>,
        startPointer: PointF,
        endPointer: PointF,
    ): List<WordInfo> {
        val result = mutableListOf<WordInfo>()

        val avgWordHeight = words.map { it.rect.height() }.average().toFloat()
        val avgWordWidth = words.map { it.rect.width() }.average().toFloat()

        val minY = minOf(startPointer.y, endPointer.y) - 2
        val maxY = maxOf(startPointer.y, endPointer.y) + 2

        val inverted = startPointer.y > endPointer.y

        val intersectRect = words.filter { word ->
            val top = word.rect.bottom - word.rect.height() / 2
            val bottom = word.rect.bottom
            top <= maxY && bottom >= minY
        }

        val groupedByOrder = linkedMapOf<Int, MutableList<WordInfo>>()
        var groupId = 0
        var previousGroupId: Float? = null
        for (word in intersectRect) {
            val currentGroupId = word.columnGroupId
            if (currentGroupId != previousGroupId) {
                groupId++
            }
            val list = groupedByOrder.getOrPut(groupId) { mutableListOf() }
            list.add(word)
            previousGroupId = currentGroupId
        }

        for ((_, wordOfGroup) in groupedByOrder) {
            if (wordOfGroup.first().columnGroupId == null) {

                val linesMap: Map<Float, List<WordInfo>> = wordOfGroup
                    .groupBy {
                        it.lineYKey.toFloat()
                    }

                val sortedLines = linesMap.toSortedMap()

                val firstLineIdx = sortedLines.keys.firstOrNull()
                val lastLineIdx = sortedLines.keys.lastOrNull()

                if (wordOfGroup.map { it.lineYKey }.distinct().size == 1) {

                    val firstLineKey = wordOfGroup.first()
                    val lastLineKey = wordOfGroup.last()

                    val lowestWords = wordOfGroup.maxOf { it.rect.bottom } + 2
                    val highestWords = wordOfGroup.minOf { it.rect.top } - 2

                    if (endPointer.y > lowestWords) {
                        result.addAll(wordOfGroup.filter {
                            it.rect.left >= startPointer.x && it.rect.right <= lastLineKey.rect.right
                        })
                    } else if (startPointer.y > lowestWords) {
                        result.addAll(wordOfGroup.filter {
                            it.rect.left >= endPointer.x && it.rect.right <= lastLineKey.rect.right
                        })
                    } else if (startPointer.y < highestWords) {
                        result.addAll(wordOfGroup.filter {
                            it.rect.left >= firstLineKey.rect.left && it.rect.right <= endPointer.x
                        })
                    } else if (endPointer.y < highestWords) {
                        result.addAll(wordOfGroup.filter {
                            it.rect.left >= firstLineKey.rect.left && it.rect.right <= startPointer.x
                        })
                    } else {
                        result.addAll(wordOfGroup.filter {
                            if (startPointer.x < endPointer.x)
                                it.rect.left >= startPointer.x && it.rect.right <= endPointer.x
                            else
                                it.rect.left >= endPointer.x && it.rect.right <= startPointer.x
                        })
                    }
                } else {
                    for ((lineIdx, lineWords) in sortedLines) {
                        when (lineIdx) {
                            firstLineIdx -> {
                                val isAboveLine = lineWords.any {
                                    it.rect.top > minY
                                }
                                result.addAll(
                                    if (isAboveLine) lineWords
                                    else if (!inverted) lineWords.filter { it.rect.left >= startPointer.x }
                                    else lineWords.filter { it.rect.left >= endPointer.x }
                                )
                            }

                            lastLineIdx -> {
                                val isBottomLine = lineWords.any {
                                    it.rect.bottom < maxY - 7
                                }
                                result.addAll(
                                    if (isBottomLine) lineWords
                                    else if (!inverted) lineWords.filter { it.rect.right <= endPointer.x }
                                    else lineWords.filter { it.rect.right <= startPointer.x }
                                )
                            }

                            else -> result.addAll(lineWords)
                        }
                    }
                }
            } else {
                val wordsOfGroupSorted = words
                    .filter { it.columnGroupId == wordOfGroup.first().columnGroupId }
                    .sortedWith(compareBy<WordInfo> { it.columnIndex!! }
                        .thenBy { it.lineYKey }.thenBy { it.rect.left })

                val highestWords = wordsOfGroupSorted.minOfOrNull { it.rect.top } ?: 0f
                val lowestWords = wordsOfGroupSorted.maxOfOrNull { it.rect.bottom } ?: 0f

                val minX =
                    if (startPointer.y < highestWords || endPointer.y < highestWords) wordsOfGroupSorted.first().rect.left
                    else if (startPointer.y > lowestWords) endPointer.x
                    else if (endPointer.y > lowestWords) startPointer.x
                    else minOf(startPointer.x, endPointer.x)

                val maxX =
                    if (startPointer.y > lowestWords || endPointer.y > lowestWords) wordsOfGroupSorted.last().rect.right
                    else if (startPointer.y < highestWords) endPointer.x
                    else if (endPointer.y < highestWords) startPointer.x
                    else maxOf(startPointer.x, endPointer.x)

                val wordInPointer = wordOfGroup
                    .filter { it.columnIndex != null && it.rect.left >= minX - 2 * avgWordWidth && it.rect.right <= maxX + 2 * avgWordWidth }
                    .sortedWith(
                        compareBy(
                            { it.columnIndex },
                            { it.lineYKey.toFloat() },
                            { it.rect.left }
                        ))
                val firstWord = wordInPointer.firstOrNull()
                val lastWord = wordInPointer.lastOrNull()


                val columnGroups: Map<Int, List<WordInfo>> =
                    wordsOfGroupSorted.filter { it.columnIndex != null }.groupBy {
                        it.columnIndex!!
                    }

                val columnLineGroups: Map<Int, Map<Int, List<WordInfo>>> =
                    columnGroups.mapValues { (_, wordsInColumn) ->
                        wordsInColumn.groupBy { word ->
                            word.lineYKey
                        }
                    }

                var tempStart = startPointer
                var tempEnd = endPointer
                if (firstWord != null && lastWord != null) {
                    if (firstWord.columnIndex != lastWord.columnIndex) {
                        if (startPointer.y < highestWords) {
                            tempStart = PointF(firstWord.rect.left, firstWord.rect.bottom)
                        } else if (endPointer.y < highestWords) {
                            tempEnd = PointF(firstWord.rect.left, firstWord.rect.bottom)
                        }
                        if (startPointer.y > lowestWords) {
                            tempStart = PointF(lastWord.rect.right, lastWord.rect.bottom)
                        } else if (endPointer.y > lowestWords) {
                            tempEnd = PointF(lastWord.rect.right, lastWord.rect.bottom)
                        }
                    }
                    var firstWordInfo: WordInfo? = null
                    var lastWordInfo: WordInfo? = null
                    val wordAtPointStart = findWordAtPoint(tempStart, wordInPointer)
                    val wordAtPointEnd = findWordAtPoint(tempEnd, wordInPointer)

                    if (wordAtPointStart != null && wordAtPointEnd != null) {
                        firstWordInfo =
                            if (wordAtPointStart.columnIndex == firstWord.columnIndex) wordAtPointStart else wordAtPointEnd
                        lastWordInfo =
                            if (wordAtPointEnd.columnIndex == lastWord.columnIndex) wordAtPointEnd else wordAtPointStart
                    }

                    for ((columnKey, linesInColumn) in columnLineGroups.toSortedMap()) {
                        for ((lineKey, wordsInLine) in linesInColumn.toSortedMap(compareBy { it })) {
                            val soredLineText = wordsInLine.sortedBy { it.rect.left }
                            if (lastWord.columnIndex == firstWord.columnIndex && columnKey == lastWord.columnIndex) {
                                if (lastWord.lineYKey == firstWord.lineYKey && firstWord.lineYKey == lineKey) {
                                    result.addAll(soredLineText.filter {
                                        it.rect.right >= minX && it.rect.left <= maxX
                                    })
                                } else {
                                    result.addAll(soredLineText.filter {
                                        when {
                                            lineKey == firstWord.lineYKey ->
                                                if (startPointer.y < highestWords || endPointer.y < highestWords) {
                                                    true
                                                } else if (!inverted) {
                                                    it.rect.left >= startPointer.x
                                                } else {
                                                    it.rect.left >= endPointer.x
                                                }

                                            lineKey == lastWord.lineYKey -> {
                                                if (startPointer.y > lowestWords || endPointer.y > lowestWords) {
                                                    true
                                                } else if (!inverted) {
                                                    it.rect.right <= endPointer.x
                                                } else {
                                                    it.rect.right <= startPointer.x
                                                }
                                            }

                                            lineKey > firstWord.lineYKey && lineKey < lastWord.lineYKey -> true
                                            else -> false
                                        }
                                    })
                                }
                            } else if (firstWordInfo != null && lastWordInfo != null && lastWord.columnIndex != firstWord.columnIndex) {
                                result.addAll(soredLineText.filter {
                                    when {
                                        lineKey == firstWordInfo.lineYKey && columnKey == firstWordInfo.columnIndex -> {
                                            if (wordAtPointStart == firstWordInfo) {
                                                it.rect.left >= tempStart.x
                                            } else if (wordAtPointEnd == firstWordInfo) {
                                                it.rect.left >= tempEnd.x
                                            } else {
                                                true
                                            }
                                        }

                                        lineKey == lastWordInfo.lineYKey && columnKey == lastWordInfo.columnIndex -> {
                                            if (wordAtPointStart == lastWordInfo) {
                                                it.rect.right <= tempStart.x
                                            } else if (wordAtPointEnd == lastWordInfo) {
                                                it.rect.right <= tempEnd.x
                                            } else {
                                                true
                                            }
                                        }

                                        lineKey != null && (lineKey > firstWordInfo.lineYKey && columnKey == firstWordInfo.columnIndex
                                                || (columnKey > firstWordInfo.columnIndex!! && columnKey < lastWordInfo.columnIndex!!)
                                                || lineKey < lastWordInfo.lineYKey && columnKey == lastWordInfo.columnIndex) -> true

                                        else -> false
                                    }
                                })
                            }
                        }
                    }
                }

            }
        }

        return result
    }

    private fun findWordAtPoint(
        point: PointF,
        words: List<WordInfo>,
        tolerance: Float = 10f
    ): WordInfo? {
        // 1. Ưu tiên tìm trong vùng mở rộng
        val found = words.firstOrNull { word ->
            val expanded = RectF(
                word.rect.left - tolerance,
                word.rect.top - tolerance,
                word.rect.right + tolerance,
                word.rect.bottom + tolerance
            )
            expanded.contains(point.x, point.y)
        }
        if (found != null) return found

        // 2. Nếu không tìm thấy, tìm từ gần nhất (fallback)
        return words.minByOrNull { word ->
            val centerX = (word.rect.left + word.rect.right) / 2
            val centerY = (word.rect.top + word.rect.bottom) / 2
            val dx = centerX - point.x
            val dy = centerY - point.y
            dx * dx + dy * dy // Euclidean distance squared (fast)
        }
    }

    fun groupWordsByLine(words: List<WordInfo>, threshold: Float = 10f): Map<Int, List<WordInfo>> {
        val sorted = words.sortedBy { it.rect.top }
        val lineTops = mutableListOf<Float>()
        val lineMap = mutableMapOf<Int, MutableList<WordInfo>>()

        var currentLine = 0
        var lastTop = Float.NEGATIVE_INFINITY

        for (word in sorted) {
            if (abs(word.rect.top - lastTop) > threshold) {
                currentLine++
                lastTop = word.rect.top
                lineTops.add(lastTop)
            }
            lineMap.getOrPut(currentLine) { mutableListOf() }.add(word)
        }

        for ((lineIntKey, lineWords) in lineMap) {
            for (word in lineWords) {
                word.lineYKey = lineIntKey
            }
        }
        return lineMap
    }

    override fun groupWords(
        allWords: List<WordInfo>,
        horizontalGapThreshold: Float,
        avgWordHeight: Float,
        minConsecutiveLines: Int,
    ): List<WordInfo> {

        if (allWords.isEmpty()) return emptyList()

        val wordsProcessed = allWords
            .map { it.copy() }

        val linesMap =
            groupWordsByLine(
                wordsProcessed,
                avgWordHeight
            ).toSortedMap(compareBy { it })

        data class GapGroupInfo(
            val lines: MutableSet<Int>,
            var currentEpsilonX: Float,
            var lastLineIntKeyAdded: Int? = null
        )

        val defaultEpsilonX = 15f
        val maxAllowedEpsilonX = 100f
        val minAllowedEpsilonX = 5f

        val gapGroups = sortedMapOf<Float, GapGroupInfo>()

        for ((lineIntKey, lineWords) in linesMap) {
            val sortedWordsInLine = lineWords.sortedBy { it.rect.left }

            // B1: TÍNH DANH SÁCH gapX CỦA DÒNG HIỆN TẠI
            val currentGapXList = mutableListOf<Float>()
            for (i in 0 until sortedWordsInLine.size - 1) {
                val word1 = sortedWordsInLine[i]
                val word2 = sortedWordsInLine[i + 1]
                val gap = word2.rect.left - word1.rect.right
                if (gap > horizontalGapThreshold) {
                    currentGapXList.add(word2.rect.left)
                }
            }

            // B2: XỬ LÝ CÁC GAPX NHƯ BÌNH THƯỜNG
            gapGroups.forEach { (_, info) ->
                if (info.lastLineIntKeyAdded != null) {
                    val linesSinceLastAdd = lineIntKey - info.lastLineIntKeyAdded!!
                    if (linesSinceLastAdd > 5) {
                        if (info.currentEpsilonX > defaultEpsilonX) {
                            info.currentEpsilonX =
                                maxOf(defaultEpsilonX, info.currentEpsilonX * 0.9f)
                        } else if (info.currentEpsilonX < defaultEpsilonX && info.currentEpsilonX > minAllowedEpsilonX) {
                            info.currentEpsilonX = defaultEpsilonX
                        }
                    }
                }
            }

            for (gapX in currentGapXList) {
                var bestNearbyX: Float? = null
                var minDiff = Float.MAX_VALUE

                gapGroups.forEach { (key, info) ->
                    val diff = abs(key - gapX)
                    if (diff < info.currentEpsilonX && diff < minDiff) {
                        minDiff = diff
                        bestNearbyX = key
                    }
                }

                if (bestNearbyX != null) {
                    val nearbyX = bestNearbyX
                    val nearbyGroupInfo = gapGroups[nearbyX]!!

                    if (gapX < nearbyX) {
                        val oldEpsilonX = nearbyGroupInfo.currentEpsilonX
                        val reductionAmount = nearbyX - gapX
                        val newEpsilonX = maxOf(minAllowedEpsilonX, oldEpsilonX - reductionAmount)

                        val linesToMove = nearbyGroupInfo.lines
                        gapGroups.remove(nearbyX)
                        val newInfo = GapGroupInfo(
                            lines = (gapGroups[gapX]?.lines ?: mutableSetOf()).apply {
                                addAll(linesToMove)
                                add(lineIntKey)
                            },
                            currentEpsilonX = newEpsilonX,
                            lastLineIntKeyAdded = lineIntKey
                        )
                        gapGroups[gapX] = newInfo
                    } else {
                        if (gapX > nearbyX) {
                            val increaseAmount = gapX - nearbyX
                            nearbyGroupInfo.currentEpsilonX = minOf(
                                nearbyGroupInfo.currentEpsilonX + increaseAmount,
                                maxAllowedEpsilonX
                            )
                        }

                        nearbyGroupInfo.lines.add(lineIntKey)
                        nearbyGroupInfo.lastLineIntKeyAdded = lineIntKey
                    }
                } else {
                    gapGroups[gapX] = GapGroupInfo(
                        lines = mutableSetOf(lineIntKey),
                        currentEpsilonX = defaultEpsilonX,
                        lastLineIntKeyAdded = lineIntKey
                    )
                }
            }

            val checkCouldGapX = sortedWordsInLine.firstOrNull()?.rect?.left
            if (checkCouldGapX != null) {
                var bestNearbyXForCheck: Float? = null
                var minDiffForCheck = Float.MAX_VALUE
                gapGroups.forEach { (key, info) ->
                    val diff = abs(key - checkCouldGapX)
                    if (diff < info.currentEpsilonX && diff < minDiffForCheck) {
                        minDiffForCheck = diff
                        bestNearbyXForCheck = key
                    }
                }
                if (bestNearbyXForCheck != null) {
                    val groupInfo = gapGroups[bestNearbyXForCheck]
                    groupInfo?.lines?.add(lineIntKey)
                    groupInfo?.lastLineIntKeyAdded = lineIntKey
                }
            }

            val firstGapXFromPrevLine =
                gapGroups.filter { (_, info) -> lineIntKey - 1 in info.lines }.keys.minOrNull()
            val checkCouldGapXFirstColumn = sortedWordsInLine.last().rect.right
            if (firstGapXFromPrevLine != null && checkCouldGapXFirstColumn < firstGapXFromPrevLine) {
                val groupInfo = gapGroups[firstGapXFromPrevLine]
                if (groupInfo != null) {
                    groupInfo.lines.add(lineIntKey)
                    groupInfo.lastLineIntKeyAdded = lineIntKey
                }
            }

            // B3: SAU KHI ĐÃ ADD — MỚI SO SÁNH VỚI DÒNG TRƯỚC
            val prevLineKey = lineIntKey - 1
            val prevLineWords = linesMap[prevLineKey]
            if (prevLineWords != null) {
//                val sortedPrevWords = prevLineWords.sortedBy { it.rect.left }
//                val prevGapXList = mutableListOf<Float>()
//                for (i in 0 until sortedPrevWords.size - 1) {
//                    val w1 = sortedPrevWords[i]
//                    val w2 = sortedPrevWords[i + 1]
//                    val gap = w2.rect.left - w1.rect.right
//                    if (gap > horizontalGapThreshold) {
//                        prevGapXList.add(w2.rect.left)
//                    }
//                }

                val prevGapXList = gapGroups.filter { it.value.lines.contains(prevLineKey) }.keys

                if (prevGapXList.size == currentGapXList.size && currentGapXList.isNotEmpty()) {
                    val targetGroup =
                        gapGroups.entries.find { it.value.lines.contains(prevLineKey) }
                    if (targetGroup != null) {
                        // Xoá các gapX mà dòng hiện tại vừa tạo
                        val gapKeysToRemove = gapGroups.filter { (_, group) ->
                            group.lines.size == 1 && group.lines.contains(lineIntKey)
                        }.keys

                        for (key in gapKeysToRemove) {
                            gapGroups.remove(key)
                        }

                        // Thêm dòng hiện tại vào group dòng trước
                        targetGroup.value.lines.add(lineIntKey)
                        targetGroup.value.lastLineIntKeyAdded = lineIntKey
                    }
                }
            }
        }


        val gapValid = sortedMapOf<Float, MutableSet<Int>>()
        for ((gapX, lineIndexes) in gapGroups) {
            if (lineIndexes.lines.size < minConsecutiveLines) continue

            val sortedIndexes = lineIndexes.lines.sorted()
            var maxConsecutive = 1
            var current = 1

            for (i in 1 until sortedIndexes.size) {
                if (sortedIndexes[i] == sortedIndexes[i - 1] + 1) {
                    current++
                    maxConsecutive = maxOf(maxConsecutive, current)
                } else {
                    current = 1
                }
            }

            if (maxConsecutive >= minConsecutiveLines) {
                gapValid[gapX] = lineIndexes.lines
            }
        }

        val groupGapValid = sortedMapOf<Float, MutableSet<Float>>()
        val mergedGroups = mutableListOf<MutableSet<Float>>()

        for ((gapX, lines) in gapValid) {
            var merged = false
            for (group in mergedGroups) {
                val groupLines = group.flatMap { gapValid[it] ?: emptySet() }.toSet()
                if (groupLines.intersect(lines).isNotEmpty()) {
                    group.add(gapX)
                    merged = true
                    break
                }
            }
            if (!merged) {
                mergedGroups.add(mutableSetOf(gapX))
            }
        }

        for (group in mergedGroups) {
            val key = group.minOrNull() ?: continue
            groupGapValid[key] = group.toMutableSet()
        }

        val sortedGroupsGapX = gapValid.toSortedMap()

        for ((lineIntKey, lineWords) in linesMap) {
            val sortedWordsInLine = lineWords.sortedBy { it.rect.left }
            val belongsToAnyColumn = gapValid.values.any { lineIntKey in it }

            for (word in sortedWordsInLine) {
                if (belongsToAnyColumn) {
                    val cx = word.rect.centerX()
                    var columnIndex = 0

                    for ((gapX, lineIndexes) in sortedGroupsGapX) {
                        if (word.lineYKey in lineIndexes) {
                            val groupKey =
                                groupGapValid.entries.find { (_, gap) -> gapX in gap }?.key
                            word.columnGroupId = groupKey
                            if (cx > gapX) {
                                val indexColumnInGap =
                                    groupGapValid.entries.find { (_, gap) -> gapX in gap }?.value
                                if (indexColumnInGap != null) {
                                    columnIndex = indexColumnInGap.sorted().indexOf(gapX) + 1
                                }
                            } else {
                                break
                            }
                        }
                    }
                    word.columnIndex = columnIndex
                } else {
                    word.columnIndex = -1
                }
            }
        }

        return wordsProcessed.sortedWith(
            compareBy<WordInfo> { it.lineYKey }
                .thenBy { it.columnIndex }
                .thenBy { it.rect.left }
        )
    }

    fun averageDistanceInLine(words: List<WordInfo>): Float {
        if (words.size < 2) return 0f
        var total = 0f
        var count = 0f

        for (i in 0 until words.size - 1) {
            val w1 = words[i].rect
            val w2 = words[i + 1].rect

            val distance = w2.left - w1.right
            if (distance > 0 && distance > 2f) {
                total += distance
                count++
            }
        }

        return total / count
    }

    private class CustomTextStripper(private val pageIndex: Int) : PDFTextStripper() {
        val wordInfoList = mutableListOf<WordInfo>()

        override fun processTextPosition(text: TextPosition?) {
            if (text == null || text.unicode.isEmpty()) return

            val font = text.font
            val fontSize = text.fontSizeInPt

            val ascent = font.fontDescriptor?.ascent?.div(1000f)?.times(fontSize) ?: text.height

            val left = text.xDirAdj
            val top = text.yDirAdj - ascent - text.height / 2
            val right = left + text.width
            val bottom = text.yDirAdj + text.height / 2

            val rect = RectF(
                left,
                top,
                right,
                bottom
            )

            wordInfoList.add(WordInfo(text.unicode, rect, pageIndex))
        }
    }
}