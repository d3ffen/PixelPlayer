package com.theveloper.pixelplay.data.network.musicbrainz

import com.google.gson.annotations.SerializedName

data class MBReleaseSearchResponse(
    @SerializedName("releases") val releases: List<MBRelease> = emptyList()
)

data class MBRelease(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("date") val date: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("artist-credit") val artistCredit: List<MBArtistCredit> = emptyList(),
    @SerializedName("media") val media: List<MBMedium> = emptyList(),
    @SerializedName("release-group") val releaseGroup: MBReleaseGroup? = null
)

data class MBArtistCredit(
    @SerializedName("name") val name: String = "",
    @SerializedName("artist") val artist: MBArtist = MBArtist()
)

data class MBArtist(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = ""
)

data class MBMedium(
    @SerializedName("position") val position: Int = 1,
    @SerializedName("track-count") val trackCount: Int = 0,
    @SerializedName("tracks") val tracks: List<MBTrack> = emptyList()
)

data class MBTrack(
    @SerializedName("number") val number: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("position") val position: Int = 0,
    @SerializedName("recording") val recording: MBRecording? = null
)

data class MBRecording(
    @SerializedName("id") val id: String = "",
    @SerializedName("length") val length: Int? = null
)

data class MBReleaseGroup(
    @SerializedName("id") val id: String = "",
    @SerializedName("primary-type") val primaryType: String? = null
)
