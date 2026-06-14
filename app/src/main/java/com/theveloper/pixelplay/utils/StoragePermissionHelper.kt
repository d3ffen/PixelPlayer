package com.theveloper.pixelplay.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Centralized runtime permission checks and launcher strings for reading/writing
 * audio files across all supported Android versions (API 29–35).
 *
 * Permissions by API level:
 * - API ≤ 29: READ_EXTERNAL_STORAGE + WRITE_EXTERNAL_STORAGE
 * - API 30–32: READ_EXTERNAL_STORAGE (write via MediaStore, no runtime permission)
 * - API 33+:   READ_MEDIA_AUDIO (write via MediaStore, no runtime permission)
 */
object StoragePermissionHelper {

    /**
     * Returns true if the app can read audio files from shared storage.
     */
    fun hasReadPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Returns true if the app can write to external storage on this Android version.
     * On API 30+ (scoped storage), writes go through MediaStore.createWriteRequest
     * and no runtime permission is needed.
     */
    fun hasWritePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Returns the permission string(s) that must be requested at runtime
     * before reading or modifying audio files.
     */
    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }
}
