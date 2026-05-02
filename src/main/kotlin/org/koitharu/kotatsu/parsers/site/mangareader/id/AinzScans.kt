package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.parseJson
import java.util.*

@MangaSourceParser("AINZSCANS", "AinzScans", "id")
internal class AinzScans(context: MangaLoaderContext) :
    MangaReaderParser(context, MangaParserSource.AINZSCANS, "v1.ainzscans01.com", pageSize = 22, searchPageSize = 10) {

    private val apiDomain = "api.ainzscans01.com"

    override val listUrl = "/allcomic"
    override val datePattern = "MMM d, yyyy"
    override val sourceLocale: Locale = Locale.ENGLISH

    override val filterCapabilities: MangaListFilterCapabilities
        get() = super.filterCapabilities.copy(
            isTagsExclusionSupported = false,
        )

    override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        val url = "https://$apiDomain/api/search?type=COMIC&limit=22&page=$page&sort=latest&order=desc"
        val json = webClient.httpGet(url).parseJson().getJSONObject("data")
        return json.getJSONArray("data").mapJSON { j ->
            Manga(
                id = generateUid(j.getString("slug")),
                url = "/comic/${j.getString("slug")}",
                publicUrl = "https://v1.ainzscans01.com/comic/${j.getString("slug")}",
                coverUrl = j.getString("poster_image_url"),
                title = j.getString("title"),
                altTitles = emptySet(),
                rating = j.getString("rating_average").toFloatOrNull() ?: 0f,
                tags = emptySet(),
                authors = emptySet(),
                state = null,
                source = source,
                contentRating = if (j.getInt("is_adult") == 1) ContentType.HENTAI else null,
            )
        }
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val url = "https://$apiDomain/api/series/comic/${manga.url.substringAfterLast('/')}"
        val json = webClient.httpGet(url).parseJson()

        val chapters = super.getDetails(manga).chapters // Fallback to HTML scraping for chapters

        return manga.copy(
            title = json.getString("title"),
            description = json.getString("synopsis"),
            state = when (json.getString("series_status")) {
                "ONGOING" -> org.koitharu.kotatsu.parsers.model.MangaState.ONGOING
                "COMPLETED" -> org.koitharu.kotatsu.parsers.model.MangaState.FINISHED
                else -> null
            },
            authors = setOfNotNull(json.getStringOrNull("author_name")),
            tags = json.getJSONArray("genres").mapJSON { g ->
                org.koitharu.kotatsu.parsers.model.MangaTag(
                    key = g.getString("slug"),
                    title = g.getString("name"),
                    source = source
                )
            }.toSet(),
            chapters = chapters
        )
    }
}
