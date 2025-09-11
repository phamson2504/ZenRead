package com.zenread.book.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.zenread.book.data.dot.BookEntity
import com.zenread.book.data.local.dao.BookDao

@Database(
    entities = [BookEntity::class],
    version = 1
)
abstract class BookDatabase : RoomDatabase() {
    abstract val bookDao: BookDao
}