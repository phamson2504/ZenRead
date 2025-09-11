package com.zenread.book.di

import com.zenread.book.data.convert.pdf.PdfFileConvert
import com.zenread.book.data.repository.BookRepositoryImpl
import com.zenread.book.data.repository.FileManagerRepositoryImpl
import com.zenread.book.domain.repository.BookRepository
import com.zenread.book.domain.repository.FileConvert
import com.zenread.book.domain.repository.FileManagerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFileSystemRepository(
        filesManagerRepositoryImpl: FileManagerRepositoryImpl
    ): FileManagerRepository

    @Binds
    @Singleton
    abstract fun bindBookRepository(
        bookRepositoryImpl: BookRepositoryImpl
    ): BookRepository

    @Binds
    @Singleton
    abstract fun bindFileConvert(
        pdfFileConvert: PdfFileConvert
    ): FileConvert
}