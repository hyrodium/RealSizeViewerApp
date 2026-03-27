package io.github.hyrodium.realsizeviewerapp.di

import android.content.ContentResolver
import android.content.Context
import io.github.hyrodium.realsizeviewerapp.data.PdfRepository
import io.github.hyrodium.realsizeviewerapp.data.SvgRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver {
        return context.contentResolver
    }

    @Provides
    @Singleton
    fun providePdfRepository(contentResolver: ContentResolver): PdfRepository {
        return PdfRepository(contentResolver)
    }

    @Provides
    @Singleton
    fun provideSvgRepository(
        contentResolver: ContentResolver,
        @ApplicationContext context: Context,
    ): SvgRepository {
        return SvgRepository(contentResolver, context)
    }
}
