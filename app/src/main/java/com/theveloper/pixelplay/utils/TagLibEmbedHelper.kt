package com.theveloper.pixelplay.utils

import android.content.Context
import android.media.MediaScannerConnection
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import com.theveloper.pixelplay.data.media.CoverArtUpdate
import com.theveloper.pixelplay.data.media.MetadataEditError
import com.theveloper.pixelplay.data.media.SongMetadataEditResult
import com.theveloper.pixelplay.data.media.SongMetadataEditor
import timber.log.Timber
import java.io.File

/**
 * Lightweight helper that wraps [SongMetadataEditor] for batch album-level
 * metadata and cover art embedding operations.
 *
 * All writes use the existing battle-tested TagLib/JAudioTagger/VorbisJava
 * pipeline in SongMetadataEditor, which supports MP3 (ID3v2), FLAC, M4A/MP4,
 * Opus, Ogg Vorbis, and WAV containers.
 */
object TagLibEmbedHelper {

    /**
     * Embeds album art bytes into an audio file.
     * Delegates to the same TagLib pipeline used by the manual Tag Editor.
     *
     * @param context Application context
     * @param filePath Absolute path to the audio file
     * @param imageBytes JPEG or PNG image bytes
     * @param mimeType MIME type of the image (default "image/jpeg")
     * @return true if the cover art was successfully embedded
     */
    fun embedAlbumArt(
        context: Context,
        filePath: String,
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg"
    ): Boolean {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.canWrite()) {
                Timber.w("Cannot embed album art: file not found or not writable: $filePath")
                return false
            }

            // Use TagLib directly via ParcelFileDescriptor (same approach as SongMetadataEditor)
            android.os.ParcelFileDescriptor.open(
                file,
                android.os.ParcelFileDescriptor.MODE_READ_WRITE
            ).use { fd ->
                val pictures = arrayOf(
                    Picture(
                        data = imageBytes,
                        description = "Front Cover",
                        pictureType = "Front Cover",
                        mimeType = mimeType
                    )
                )
                val pictureFd = fd.dup()
                val success = TagLib.savePictures(pictureFd.detachFd(), pictures)
                if (!success) {
                    Timber.w("TagLib.savePictures returned false for $filePath")
                }
                success
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to embed album art in $filePath")
            false
        }
    }

    /**
     * Writes textual metadata fields into an audio file using TagLib's property map API.
     *
     * @return true if all fields were successfully written
     */
    fun embedTrackMetadata(
        filePath: String,
        title: String? = null,
        artist: String? = null,
        albumArtist: String? = null,
        album: String? = null,
        year: String? = null,
        trackNumber: Int? = null
    ): Boolean {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.canWrite()) {
                Timber.w("Cannot embed metadata: file not found or not writable: $filePath")
                return false
            }

            android.os.ParcelFileDescriptor.open(
                file,
                android.os.ParcelFileDescriptor.MODE_READ_WRITE
            ).use { fd ->
                val metadataFd = fd.dup()
                val existingMetadata = TagLib.getMetadata(metadataFd.detachFd())
                val propertyMap = HashMap(existingMetadata?.propertyMap ?: emptyMap())

                title?.let { propertyMap["TITLE"] = arrayOf(it) }
                artist?.let { propertyMap["ARTIST"] = arrayOf(it) }
                albumArtist?.let { propertyMap["ALBUMARTIST"] = arrayOf(it) }
                album?.let { propertyMap["ALBUM"] = arrayOf(it) }
                year?.let { propertyMap["DATE"] = arrayOf(it) }
                trackNumber?.let { propertyMap["TRACKNUMBER"] = arrayOf(it.toString()) }

                val saveFd = fd.dup()
                val success = TagLib.savePropertyMap(saveFd.detachFd(), propertyMap)
                if (!success) {
                    Timber.w("TagLib.savePropertyMap returned false for $filePath")
                }
                success
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to embed metadata in $filePath")
            false
        }
    }

    /**
     * Triggers a MediaStore rescan for the given file path so that Android's
     * media database and PixelPlayer's library pick up the tag changes.
     */
    fun rescanFile(context: Context, filePath: String) {
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(filePath),
                null
            ) { path, uri ->
                Timber.d("MediaScanner completed for $path, URI: $uri")
            }
        } catch (e: Exception) {
            Timber.w(e, "MediaScanner failed for $filePath")
        }
    }

    /**
     * Checks whether an audio file already has embedded cover art.
     * Reads the first few PICTURE blocks via TagLib.
     */
    fun hasEmbeddedArt(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (!file.exists()) return false

            android.os.ParcelFileDescriptor.open(
                file,
                android.os.ParcelFileDescriptor.MODE_READ_ONLY
            ).use { fd ->
                val metadata = TagLib.getMetadata(fd.detachFd())
                val pictures = metadata?.pictures
                pictures != null && pictures.isNotEmpty()
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to check embedded art for $filePath")
            false
        }
    }
}
