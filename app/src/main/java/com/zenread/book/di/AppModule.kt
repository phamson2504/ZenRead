package com.zenread.book.di

import android.app.Application
import androidx.room.Room
import com.zenread.book.data.local.dao.BookDao
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
        ).build()
    }

    @Provides
    @Singleton
    fun provideBookDao(db: BookDatabase): BookDao {
        return db.bookDao
    }
}