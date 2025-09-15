package com.cinepulse.providers

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class CinePulseProvider : MainAPI() {
    override var mainUrl = "https://cinepulse.cc"
    override var name = "CinePulse"
    override val hasMainPage = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "$mainUrl/films-vf/" to "Films VF",
        "$mainUrl/films-vostfr/" to "Films VOSTFR",
        "$mainUrl/series-vf/" to "Séries VF",
        "$mainUrl/series-vostfr/" to "Séries VOSTFR"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + if (page > 1) "page/$page/" else "").document
        val home = document.select("div.items article.item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.data h3 a")?.text() ?: return null
        val href = this.selectFirst("div.data h3 a")?.attr("href") ?: return null
        val posterUrl = this.selectFirst("div.poster img")?.attr("src")
        val quality = this.selectFirst("div.poster span.quality")?.text()
        
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = getQualityFromString(quality)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("div.result-item article").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        
        val title = document.selectFirst("div.sheader h1")?.text() ?: return null
        val poster = document.selectFirst("div.poster img")?.attr("src")
        val description = document.selectFirst("div.wp-content p")?.text()
        
        val recommendations = document.select("div.related div.item").mapNotNull {
            val recTitle = it.selectFirst("div.data h3 a")?.text() ?: return@mapNotNull null
            val recHref = it.selectFirst("div.data h3 a")?.attr("href") ?: return@mapNotNull null
            val recPoster = it.selectFirst("div.poster img")?.attr("src")
            
            newMovieSearchResponse(recTitle, recHref, TvType.Movie) {
                this.posterUrl = recPoster
            }
        }

        val episodes = document.select("div.episodios li").mapNotNull {
            val episodeTitle = it.selectFirst("div.epistitle a")?.text() ?: return@mapNotNull null
            val episodeHref = it.selectFirst("div.epistitle a")?.attr("href") ?: return@mapNotNull null
            val episodeNumber = it.selectFirst("div.numerando")?.text()?.split(" - ")?.get(1)?.toIntOrNull()
            
            Episode(episodeHref, episodeNumber?.toString() ?: "1", episodeNumber ?: 1)
        }

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.recommendations = recommendations
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val iframeUrl = document.selectFirst("iframe")?.attr("src") ?: return false
        
        loadExtractor(iframeUrl, data, subtitleCallback, callback)
        return true
    }

    private fun getQualityFromString(quality: String?): Qualities {
        return when {
            quality?.contains("HD", ignoreCase = true) == true -> Qualities.P720
            quality?.contains("4K", ignoreCase = true) == true -> Qualities.P2160
            quality?.contains("1080", ignoreCase = true) == true -> Qualities.P1080
            else -> Qualities.Unknown
        }
    }
}
