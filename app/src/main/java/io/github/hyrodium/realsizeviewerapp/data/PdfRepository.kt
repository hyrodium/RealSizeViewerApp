package io.github.hyrodium.realsizeviewerapp.data

import android.content.ContentResolver
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfRepository @Inject constructor(
    private val contentResolver: ContentResolver,
) {
    suspend fun openPdf(uri: Uri): PdfRendererHolder =
        withContext(Dispatchers.IO) {
            val pfd = contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalArgumentException("Cannot open: $uri")
            val renderer = PdfRenderer(pfd)
            val pageSizesPt = (0 until renderer.pageCount).map { i ->
                renderer.openPage(i).use { page -> Pair(page.width, page.height) }
            }
            PdfRendererHolder(pfd, renderer, pageSizesPt)
        }
}
