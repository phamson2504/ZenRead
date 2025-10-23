package com.zenread.book.data.convert.dot

import android.graphics.RectF
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    @TypeConverter
    fun fromRectFList(value: List<RectF>): String = Gson().toJson(value)

    @TypeConverter
    fun toRectFList(value: String): List<RectF> {
        val type = object : TypeToken<List<RectF>>() {}.type
        return Gson().fromJson(value, type)
    }

    @TypeConverter
    fun fromStringList(value: List<String>): String = Gson().toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(value, type)
    }
}