package com.ltman.photopicker_core.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.MimeTypeMap
import com.ltman.photopicker_core.media.model.MediaFile
import java.io.File
import java.net.URLConnection

object PhotoPickerUtils {

    private const val DEFAULT_DURATION_LABEL = "00:00"

    fun getNameFromFilePath(path: String): String {
        return if (path.contains(File.separator)) {
            path.substring(path.lastIndexOf(File.separator) + 1)
        } else path
    }

    fun grantAppPermission(context: Context, intent: Intent, fileUri: Uri?) {
        val resolvedIntentActivities = context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        for (resolvedIntentInfo in resolvedIntentActivities) {
            val packageName = resolvedIntentInfo.activityInfo.packageName
            context.grantUriPermission(
                packageName, fileUri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    fun revokeAppPermission(context: Context, fileUri: Uri?) {
        context.revokeUriPermission(
            fileUri,
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    fun isGifFormat(mediaFile: MediaFile): Boolean {
        return isGifFormat(mediaFile.path)
    }

    fun isGifFormat(path: String): Boolean {
        val extension = getExtension(path)
        return extension.equals("gif", ignoreCase = true)
    }

    fun isVideoFormat(image: MediaFile): Boolean {
        val extension = getExtension(image.path)
        val mimeType =
            if (extension.isEmpty()) URLConnection.guessContentTypeFromName(image.path) else MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(extension)
        return mimeType != null && mimeType.startsWith("video")
    }

    fun getDurationLabel(durationMs: Long?): String {
        // Return default duration label if null
        return durationMs?.let { duration ->
            val second = duration / 1000 % 60
            val minute = duration / (1000 * 60) % 60
            val hour = duration / (1000 * 60 * 60) % 24
            return if (hour > 0) {
                String.format("%02d:%02d:%02d", hour, minute, second)
            } else {
                String.format("%02d:%02d", minute, second)
            }
        } ?: DEFAULT_DURATION_LABEL
    }

    private fun getExtension(path: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(path)
        if (extension.isNotEmpty()) {
            return extension
        }
        return if (path.contains(".")) {
            path.substring(path.lastIndexOf(".") + 1, path.length)
        } else {
            ""
        }
    }
}