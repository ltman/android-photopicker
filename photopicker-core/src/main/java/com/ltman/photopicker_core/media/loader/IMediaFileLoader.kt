package com.ltman.photopicker_core.media.loader

import android.content.Context
import android.os.Parcelable
import com.ltman.photopicker_core.media.model.MediaFile
import kotlinx.parcelize.Parcelize
import java.io.File

interface IMediaFileLoader {
    fun loadDeviceMediaFiles(
        context: Context,
        config: Config,
        listener: Listener
    )

    fun abortLoadProcess()

    interface Listener {
        fun onMediaFileLoaded(mediaFiles: List<MediaFile>)
        fun onMediaFileFailed(error: Throwable)
    }

    @Parcelize
    class Config(
        val isFolderMode: Boolean = false,
        val isIncludeVideo: Boolean = false,
        val isOnlyVideo: Boolean = false,
        val isIncludeAnimation: Boolean = false,
        val excludedFiles: List<File> = emptyList()
    ): Parcelable
}