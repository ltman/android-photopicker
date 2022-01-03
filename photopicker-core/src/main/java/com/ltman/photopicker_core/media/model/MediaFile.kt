package com.ltman.photopicker_core.media.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MediaFile(
    val id: Long,
    val name: String,
    val path: String,
    val duration: Long
): Parcelable