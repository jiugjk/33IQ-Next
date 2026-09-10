package com.jiugjk.iq33.feature.feed.presentation.screen.imageviewer

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import coil3.ImageLoader
import coil3.SingletonImageLoader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Saves a question image into the device's picture gallery.
 *
 * The bytes come from Coil's own disk cache when the image is already there - which it is, because
 * the only way to reach this is to have been looking at the picture - and are re-fetched through
 * Coil otherwise. Going through Coil rather than a raw HTTP call means the request carries the same
 * headers the rest of the app uses; 33IQ's CDN is not guaranteed to serve an unadorned request.
 */
internal class ImageSaver(
    private val ioDispatcher: CoroutineDispatcher,
    private val okHttpClient: OkHttpClient,
) {
    /**
     * @return the display name the image was saved under, or null if it could not be saved. Callers
     *   report both outcomes to the user, so a failure here is a value rather than an exception.
     */
    suspend fun saveToGallery(
        context: Context,
        imageUrl: String,
    ): String? =
        withContext(ioDispatcher) {
            runCatching { writeToMediaStore(context, imageUrl) }
                .onFailure { error -> Timber.tag(SAVE_LOG_TAG).e(error, "Failed to save %s", imageUrl) }
                .getOrNull()
        }

    private suspend fun writeToMediaStore(
        context: Context,
        imageUrl: String,
    ): String {
        val displayName = displayNameFor(imageUrl)
        val resolver = context.contentResolver

        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeTypeFor(imageUrl))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Scoped storage: the app writes into its own folder of the shared collection
                    // without holding a storage permission at all.
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME")
                    // IS_PENDING hides the row until the bytes are written, so a gallery scanning
                    // mid-copy never shows a half-decoded image.
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

        val uri =
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("MediaStore refused an entry for $displayName")

        runCatching {
            openImageStream(context, imageUrl).use { source ->
                resolver.openOutputStream(uri)?.use { sink -> source.copyTo(sink) }
                    ?: throw IOException("Could not open $uri for writing")
            }
        }.onFailure {
            // A half-written row would sit in the gallery as a broken thumbnail forever.
            resolver.delete(uri, null, null)
        }.getOrThrow()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }

        return displayName
    }

    /** Reads the image through Coil, so a cache hit costs no network at all. */
    private suspend fun openImageStream(
        context: Context,
        imageUrl: String,
    ): InputStream {
        val imageLoader: ImageLoader = SingletonImageLoader.get(context)

        imageLoader.diskCache
            ?.openSnapshot(imageUrl)
            ?.use { snapshot -> return snapshot.data.toFile().inputStream() }

        val request = Request.Builder().url(imageUrl).build()
        val response = okHttpClient.newCall(request).execute()

        response.use { body ->
            if (!body.isSuccessful) throw IOException("HTTP ${body.code} fetching $imageUrl")

            return ByteArrayInputStream(body.body.bytes())
        }
    }

    private fun displayNameFor(imageUrl: String): String {
        val extension = imageUrl.substringAfterLast('.', "").substringBefore('?').take(EXTENSION_MAX_LENGTH)
        val suffix = extension.takeIf { it.isNotBlank() && it.all(Char::isLetterOrDigit) } ?: "jpg"

        return "${FILE_NAME_PREFIX}_${System.currentTimeMillis()}.$suffix"
    }

    private fun mimeTypeFor(imageUrl: String): String =
        when (imageUrl.substringAfterLast('.', "").substringBefore('?').lowercase()) {
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }

    private companion object {
        const val ALBUM_NAME = "33IQ"
        const val FILE_NAME_PREFIX = "33iq"
        const val EXTENSION_MAX_LENGTH = 5
        const val SAVE_LOG_TAG = "ImageSaver"
    }
}
