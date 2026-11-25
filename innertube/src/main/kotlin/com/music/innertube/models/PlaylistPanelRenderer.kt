package com.music.innertube.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class PlaylistPanelRenderer(
    val title: String?,
    val titleText: Runs?,
    val shortBylineText: Runs?,
    val contents: List<Content>,
    val isInfinite: Boolean?,
    val numItemsToShow: Int?,
    val playlistId: String?,
    val continuations: List<Continuation>?,
) {
    @Serializable
    data class Content(
        val playlistPanelVideoRenderer: PlaylistPanelVideoRenderer?,
        val automixPreviewVideoRenderer: AutomixPreviewVideoRenderer?,
        /**
         * A song that exists both as a music video and as an audio track ("Song / Video" toggle):
         * `{ primaryRenderer: {playlistPanelVideoRenderer}, counterpart: [{counterpartRenderer: {playlistPanelVideoRenderer}}] }`.
         * Kept raw and decoded leniently, so an odd shape here can't break the whole queue.
         */
        val playlistPanelVideoWrapperRenderer: JsonObject? = null,
    )
}
