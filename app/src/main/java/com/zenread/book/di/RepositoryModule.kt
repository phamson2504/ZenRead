package com.zenread.book.di

import com.zenread.book.data.convert.pdf.PdfFileConvert
import com.zenread.book.data.file.DiskCacheManagerImpl
import com.zenread.book.data.file.PageCountManagerImpl
import com.zenread.book.data.parser.pdf.PdfTextParser
import com.zenread.book.data.parser.pdf.PdfTextParserImpl
import com.zenread.book.data.repository.BookRepositoryImpl
import com.zenread.book.data.repository.BookmarkRepositoryImpl
import com.zenread.book.data.repository.FileManagerRepositoryImpl
import com.zenread.book.data.repository.HighlightRepositoryImpl
import com.zenread.book.data.repository.PdfRendererManagerImpl
import com.zenread.book.data.repository.PermissionRepositoryImpl
import com.zenread.book.domain.repository.BookRepository
import com.zenread.book.domain.repository.BookmarkRepository
import com.zenread.book.domain.repository.DiskCacheManager
import com.zenread.book.domain.repository.FileConvert
import com.zenread.book.domain.repository.FileManagerRepository
import com.zenread.book.domain.repository.HighlightRepository
import com.zenread.book.domain.repository.PageCountManager
import com.zenread.book.domain.repository.PdfRendererManager
import com.zenread.book.domain.repository.PermissionRepository
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
    abstract fun bindHighlightRepository(
        highlightRepositoryImpl: HighlightRepositoryImpl
    ): HighlightRepository

    @Binds
    @Singleton
    abstract fun bindBookmarkRepository(
        bookmarkRepositoryImpl: BookmarkRepositoryImpl
    ): BookmarkRepository

    @Binds
    @Singleton
    abstract fun bindFileConvert(
        pdfFileConvert: PdfFileConvert
    ): FileConvert

    @Binds
    @Singleton
    abstract fun bindPermissionRepository(
        permissionRepositoryImpl: PermissionRepositoryImpl
    ): PermissionRepository

    @Binds
    @Singleton
    abstract fun bindPdfRendererManager(
        impl: PdfRendererManagerImpl
    ): PdfRendererManager

    @Binds
    @Singleton
    abstract fun bindDiskCacheManager(
        diskCacheManagerImpl: DiskCacheManagerImpl
    ): DiskCacheManager

    @Binds
    @Singleton
    abstract fun bindPageCountManager(
        pageCountManagerImpl: PageCountManagerImpl
    ): PageCountManager

    @Binds
    @Singleton
    abstract fun bindPdfTextParser(
        pdfTextParserImpl: PdfTextParserImpl
    ): PdfTextParser


}