package io.github.hyrodium.realsizeviewerapp.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SvgRepository @Inject constructor(
    private val contentResolver: ContentResolver,
    private val context: Context,
) {
    suspend fun loadSvgFromUri(uri: Uri): SVG = withContext(Dispatchers.IO) {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            SVG.getFromInputStream(inputStream)
        } ?: throw IllegalArgumentException("Cannot open input stream for URI: $uri")
    }

    suspend fun loadSvgFromAssets(fileName: String): SVG = withContext(Dispatchers.IO) {
        context.assets.open(fileName).use { inputStream ->
            SVG.getFromInputStream(inputStream)
        }
    }
}
