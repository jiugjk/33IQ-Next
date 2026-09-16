package com.jiugjk.iq33.feature.feed.presentation.screen.imageviewer

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import coil3.ImageLoader
import coil3.SingletonImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Saves a question image into the device's picture gallery.
 *
 * The bytes come from Coil's own disk cache when the image is already there - which it usually is,
 * because the user is viewing the picture - and are re-fetched through the shared OkHttpClient
 * otherwise. Streaming the response directly to MediaStore avoids large in-memory allocations.
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
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    Timber.tag(SAVE_LOG_TAG).e(error, "Failed to save %s", imageUrl)
                }.getOrNull()
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

        try {
            copyStreamToUri(context, imageUrl, uri)
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Throwable,
        ) {
            // A half-written or aborted row would sit in the gallery as a broken thumbnail forever.
            resolver.delete(uri, null, null)
            throw error
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }

        return displayName
    }

    /** Streams the image directly from Coil cache or OkHttp to MediaStore. */
    private suspend fun copyStreamToUri(
        context: Context,
        imageUrl: String,
        uri: Uri,
    ) {
        if (copyFromDiskCache(context, imageUrl, uri)) return
        streamFromNetwork(context.contentResolver, imageUrl, uri)
    }

    private fun copyFromDiskCache(
        context: Context,
        imageUrl: String,
        uri: Uri,
    ): Boolean {
        val imageLoader: ImageLoader = SingletonImageLoader.get(context)
        val snapshot = imageLoader.diskCache?.openSnapshot(imageUrl) ?: return false

        snapshot.use { snap ->
            snap.data.toFile().inputStream().use { source ->
                context.contentResolver.openOutputStream(uri)?.use { sink ->
                    source.copyTo(sink)
                } ?: throw IOException("Could not open $uri for writing")
            }
        }
        return true
    }

    private suspend fun streamFromNetwork(
        resolver: ContentResolver,
        imageUrl: String,
        uri: Uri,
    ) {
        val request = Request.Builder().url(imageUrl).build()
        val call = okHttpClient.newCall(request)

        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                call.cancel()
            }
            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        try {
                            copyResponseToUri(resolver, response, imageUrl, uri)
                            if (continuation.isActive) {
                                continuation.resume(Unit)
                            }
                        } catch (
                            @Suppress("TooGenericExceptionCaught") e: Throwable,
                        ) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(e)
                            }
                        }
                    }
                },
            )
        }
    }

    private fun copyResponseToUri(
        resolver: ContentResolver,
        response: Response,
        imageUrl: String,
        uri: Uri,
    ) {
        response.use { res ->
            if (!res.isSuccessful) {
                throw IOException("HTTP ${res.code} fetching $imageUrl")
            }
            res.body.byteStream().use { source ->
                resolver.openOutputStream(uri)?.use { sink ->
                    source.copyTo(sink)
                } ?: throw IOException("Could not open $uri for writing")
            }
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
