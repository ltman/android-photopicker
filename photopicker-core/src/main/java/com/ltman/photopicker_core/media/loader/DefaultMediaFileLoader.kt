package com.ltman.photopicker_core.media.loader

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import com.ltman.photopicker_core.media.model.MediaFile
import com.ltman.photopicker_core.util.PhotoPickerUtils
import java.io.File
import java.lang.IllegalStateException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DefaultMediaFileLoader(): IMediaFileLoader {

    private var executor: ExecutorService? = null

    override fun loadDeviceMediaFiles(
        context: Context,
        config: IMediaFileLoader.Config,
        listener: IMediaFileLoader.Listener
    ) {
        getExecutorService().execute(
            ImageLoadRunnable(
                context.applicationContext,
                config,
                listener
            )
        )
    }

    override fun abortLoadProcess() {
        executor?.shutdown()
        executor = null
    }

    private fun getExecutorService(): ExecutorService {
        return executor ?: run {
            val newExecutor = Executors.newSingleThreadExecutor()
            executor = newExecutor
            newExecutor
        }
    }

    private class ImageLoadRunnable(
        private val context: Context,
        private val config: IMediaFileLoader.Config,
        private val listener: IMediaFileLoader.Listener
    ) : Runnable {

        companion object {
            private const val DEFAULT_FOLDER_NAME = "SDCARD"
            private const val FIRST_LIMIT = 1_000

            private const val QUERY_LIMIT = "limit"
        }

        private val projection_image = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        private val projection_video = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        private val projection_image_or_video = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DURATION
        )




        @SuppressLint("InlinedApi")
        private fun queryData(limit: Int? = null): Cursor? {
            val useNewApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            val sourceUri = if (limit != null && useNewApi) {
                getSourceUri().buildUpon()
                    .appendQueryParameter(QUERY_LIMIT, limit.toString())
                    .build()
            } else {
                getSourceUri()
            }

            val type = MediaStore.Files.FileColumns.MEDIA_TYPE

            val selection = when {
                config.isOnlyVideo -> "${type}=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}"
                config.isIncludeVideo -> "$type=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} OR $type=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}"
                // Empty because we query from image media store
                else -> ""
            }

            val projection = when {
                config.isOnlyVideo -> projection_video
                config.isIncludeVideo -> projection_image_or_video
                else -> projection_image
            }

            if (useNewApi) {
                val args = Bundle().apply {
                    // Sort function
                    putStringArray(
                        ContentResolver.QUERY_ARG_SORT_COLUMNS,
                        arrayOf(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    )
                    putInt(
                        ContentResolver.QUERY_ARG_SORT_DIRECTION,
                        ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                    )
                    // Selection
                    putString(
                        ContentResolver.QUERY_ARG_SQL_SELECTION,
                        selection
                    )
                    // Limit
                    if (limit != null) {
                        putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                    }
                }

                return context.contentResolver.query(sourceUri, projection, args, null)
            }

            val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC".let {
                if (limit != null) "$it LIMIT $limit" else it
            }

            return context.contentResolver.query(
                sourceUri, projection,
                selection, null, sortOrder
            )
        }

        private fun getSourceUri(): Uri {
            return if (config.isOnlyVideo || config.isIncludeVideo) {
                MediaStore.Files.getContentUri("external")
            } else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }


        private fun cursorToImage(cursor: Cursor): MediaFile? {
            return try {
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                val file = makeSafeFile(path) ?: return null
                if (config.excludedFiles.contains(file)) return null

                // Exclude GIF when we don't want it
                if (!config.isIncludeAnimation) {
                    if (PhotoPickerUtils.isGifFormat(path)) return null
                }

                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                val duration = cursor.getColumnIndex(MediaStore.Video.Media.DURATION).takeIf { it >= 0 }?.let {
                    cursor.getLong(it)
                } ?: 0

                if (name != null) {
                    MediaFile(id, name, path, duration)
                } else {
                    null
                }
            } catch (_: java.lang.IllegalArgumentException) {
                null
            }
        }

        private fun processData(cursor: Cursor?) {
            if (cursor == null) {
                listener.onMediaFileFailed(NullPointerException())
                return
            }

            val result: MutableList<MediaFile> = ArrayList()
            //val folderMap: MutableMap<String, Folder> = mutableMapOf()

            if (cursor.moveToFirst()) {
                do {
                    val image = cursorToImage(cursor)

                    if (image != null) {
                        result.add(image)

                        // Load folders
                        if (!config.isFolderMode) continue
                        var bucket = try {
                            cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME))
                        } catch (_: IllegalStateException) {
                            null
                        }
                        if (bucket == null) {
                            val parent = File(image.path).parentFile
                            bucket = if (parent != null) parent.name else DEFAULT_FOLDER_NAME
                        }

//                        if (bucket != null) {
//                            var folder = folderMap[bucket]
//                            if (folder == null) {
//                                folder = Folder(bucket)
//                                folderMap[bucket] = folder
//                            }
//                            folder.images.add(image)
//                        }
                    }

                } while (cursor.moveToNext())
            }
            cursor.close()

            //val folders = folderMap.values.toList()
            listener.onMediaFileLoaded(result)
        }

        override fun run() {
            // We're gonna load two times for faster load if the devices has many images
            val cursor = queryData(FIRST_LIMIT)
            val isLoadDataAgain = cursor?.count == FIRST_LIMIT
            processData(cursor)

            if (isLoadDataAgain) {
                processData(queryData())
            }
        }
    }

    companion object {
        private fun makeSafeFile(path: String?): File? {
            return if (path == null || path.isEmpty()) {
                null
            } else try {
                File(path)
            } catch (ignored: Exception) {
                null
            }
        }
    }
}