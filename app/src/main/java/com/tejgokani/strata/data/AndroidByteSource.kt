package com.tejgokani.strata.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.tejgokani.strata.engine.ByteSource

/** Wraps a SAF (Storage Access Framework) [Uri] as the engine's platform-agnostic [ByteSource]. */
class AndroidByteSource(
    private val resolver: ContentResolver,
    private val uri: Uri,
) : ByteSource {
    private val cachedSize: Long by lazy {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0) cursor.getLong(idx) else -1L
            } else -1L
        } ?: -1L
    }

    override fun sizeBytes(): Long = cachedSize

    override fun openStream() = resolver.openInputStream(uri)
        ?: throw java.io.IOException("Unable to open input stream for $uri")

    fun displayName(): String =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        } ?: uri.lastPathSegment ?: "unnamed"

    fun mimeType(): String = resolver.getType(uri) ?: "application/octet-stream"
}
