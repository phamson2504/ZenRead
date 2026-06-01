package com.zenread.book.di

import android.app.Application
import androidx.room.Room
import com.zenread.book.data.local.dao.BookDao
import com.zenread.book.data.local.dao.BookmarkDao
import com.zenread.book.data.local.dao.HighlightDao
import com.zenread.book.data.local.db.BookDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(app: Application): BookDatabase {
        return Room.databaseBuilder(
            app,
            BookDatabase::class.java,
            "book_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideBookDao(db: BookDatabase): BookDao {
        return db.bookDao
    }

    @Provides
    @Singleton
    fun provideHighlightDao(db: BookDatabase): HighlightDao {
        return db.highlightDao
    }

    @Provides
    @Singleton
    fun provideBookmarkDao(database: BookDatabase): BookmarkDao {
        return database.bookmarkDao
    }
}