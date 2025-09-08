package com.zenread.book.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.zenread.book.data.dot.Book
import com.zenread.book.data.local.dao.BookDao

@Database(
    entities = [Book::class],
    version = 1
)
abstract class BookDatabase : RoomDatabase() {
    abstract val bookDao: BookDao
}