package com.zenread.book.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.zenread.book.data.convert.dot.Converters
import com.zenread.book.data.dot.BookEntity
import com.zenread.book.data.dot.HighlightEntity
import com.zenread.book.data.local.dao.BookDao
import com.zenread.book.data.local.dao.HighlightDao

@Database(
    entities = [BookEntity::class, HighlightEntity::class],
    version = 1
)
@TypeConverters(Converters::class)
abstract class BookDatabase : RoomDatabase() {
    abstract val bookDao: BookDao
    abstract val highlightDao: HighlightDao
}