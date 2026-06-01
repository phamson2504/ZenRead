package com.zenread.book.presentation.pdf.gesture

import android.view.MotionEvent
import kotlin.math.sqrt

class DynamicMinSpanScaleDetector(
    private val listener: OnScaleListener
) {

    interface OnScaleListener {
        fun onScaleBegin(focusX: Float, focusY: Float): Boolean
        fun onScale(scaleFactor: Float, focusX: Float, focusY: Float): Boolean
        fun onScaleEnd()
    }

    private var inProgress = false
    private var prevSpan = 0f

    // Sử dụng ID để theo dõi 2 ngón tay đang zoom
    private var activePointerId0 = MotionEvent.INVALID_POINTER_ID
    private var activePointerId1 = MotionEvent.INVALID_POINTER_ID

    // Hằng số cho các ID không hợp lệ
    private companion object {
        const val INVALID_ID = -1
    }

    fun onTouchEvent(event: MotionEvent): Boolean {

        // 1. Xử lý các sự kiện BẮT ĐẦU CỬ CHỈ (DOWN/POINTER_DOWN)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // Nếu đang có ít nhất 2 ngón tay và chưa có cử chỉ nào tiến hành
                if (event.pointerCount >= 2 && !inProgress) {

                    // Lấy ID của hai pointer đầu tiên (Index 0 và 1)
                    activePointerId0 = event.getPointerId(0)
                    activePointerId1 = event.getPointerId(1)

                    // Tính span ban đầu
                    val span = calculateSpan(event, 0, 1)

                    if (span > 0f) {
                        val focusX = (event.getX(0) + event.getX(1)) / 2f
                        val focusY = (event.getY(0) + event.getY(1)) / 2f

                        // Gọi onScaleBegin và kiểm tra giá trị trả về
                        if (listener.onScaleBegin(focusX, focusY)) {
                            inProgress = true
                            prevSpan = span
                            return true
                        }
                    }
                }
            }
        }

        // 2. Xử lý CỬ CHỈ ĐANG TIẾN HÀNH (MOVE)
        if (inProgress) {

            // Tìm index của hai pointer ID đang active
            val pointerIndex0 = event.findPointerIndex(activePointerId0)
            val pointerIndex1 = event.findPointerIndex(activePointerId1)

            // Đảm bảo cả hai pointer vẫn còn tồn tại và là 2 ngón tay đang zoom
            if (pointerIndex0 != INVALID_ID && pointerIndex1 != INVALID_ID) {

                val span = calculateSpan(event, pointerIndex0, pointerIndex1)

                if (span > 0f && prevSpan > 0f) {
                    val scaleFactor = span / prevSpan

                    val focusX = (event.getX(pointerIndex0) + event.getX(pointerIndex1)) / 2f
                    val focusY = (event.getY(pointerIndex0) + event.getY(pointerIndex1)) / 2f

                    // Gọi onScale và kiểm tra giá trị trả về
                    if (listener.onScale(scaleFactor, focusX, focusY)) {
                        prevSpan = span // Chỉ cập nhật prevSpan nếu onScale trả về true
                        return true
                    } else {
                        // Nếu onScale trả về false (người dùng muốn ngắt)
                        inProgress = false
                        listener.onScaleEnd()
                        return true
                    }
                }
            }
        }

        // 3. Xử lý KẾT THÚC CỬ CHỈ (UP/POINTER_UP/CANCEL)
        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Kết thúc hoàn toàn khi ngón tay cuối cùng nhấc lên
                if (inProgress) {
                    inProgress = false
                    listener.onScaleEnd()
                }
                // Reset ID
                activePointerId0 = INVALID_ID
                activePointerId1 = INVALID_ID
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Kiểm tra xem ngón tay nhấc lên có phải là 1 trong 2 ngón tay zoom không
                val upPointerId = event.getPointerId(event.actionIndex)

                if (upPointerId == activePointerId0 || upPointerId == activePointerId1) {
                    // Nếu một trong hai ngón tay zoom nhấc lên, ta kết thúc zoom.
                    if (inProgress) {
                        inProgress = false
                        listener.onScaleEnd()
                    }
                    // Reset ID
                    activePointerId0 = INVALID_ID
                    activePointerId1 = INVALID_ID
                }
            }
        }

        return false // Không có cử chỉ nào được xử lý
    }

    // Hàm phụ để tính span giữa hai pointer cụ thể
    private fun calculateSpan(event: MotionEvent, index1: Int, index2: Int): Float {
        val dx = event.getX(index2) - event.getX(index1)
        val dy = event.getY(index2) - event.getY(index1)
        return sqrt(dx * dx + dy * dy)
    }
}