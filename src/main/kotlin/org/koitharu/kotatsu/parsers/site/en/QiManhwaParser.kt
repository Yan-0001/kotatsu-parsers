package org.koitharu.kotatsu.parsers.site.en

import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.InternalParsersApi
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.Jsoup.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.Jsoup.src
import org.koitharu.kotatsu.parsers.util.Jsoup.selectFirstOrThrow

@InternalParsersApi
@MangaSourceParser(
    name = "QIMANHWA",
    title = "Qi Manhwa",
    locale = "en",
    type = ContentType.MANGA
)
class QiManhwaParser(
    context: MangaLoaderContext,
    source: MangaParserSource,
) : PagedMangaParser(context, source, pageSize = 20) {

    override val configKeyDomain = ConfigKey.Domain("qimanhwa.com")

    override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        val url = if (filter.query.isNullOrEmpty()) {
            "https://$domain/browse?page=$page"
        } else {
            "https://$domain/browse?q=${filter.query}&page=$page"
        }

        val doc = webClient.httpGet(url).parseHtml()
        val mangaElements = doc.body().select("a[href^=/series/]")

        return mangaElements.mapNotNull { element ->
            val relativeUrl = element.attrAsRelativeUrl("href") ?: return@mapNotNull null
            Manga(
                id = generateUid(relativeUrl),
                title = element.text(),
                publicUrl = relativeUrl.toAbsoluteUrl(domain),
                // Other fields like cover can be extracted from child img tags
            )
        }
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val slug = manga.id.split(":").last() // Simple extraction if id is uid:slug
        val url = "https://$domain/series/$slug"
        val doc = webClient.httpGet(url).parseHtml()

        val title = doc.title()
        val description = doc.body().selectFirst("meta[name=description]")?.attr("content") ?: ""

        val chapters = doc.body().select("a[href*=/chapter-]")
            .mapNotNull { element ->
                val relativeUrl = element.attrAsRelativeUrl("href") ?: return@mapNotNull null
                val chapterNumber = relativeUrl.substringAfterLast("/chapter-").substringBefore(" ").substringBefore("-")
                    .toDoubleOrNull() ?: 0.0

                MangaChapter(
                    id = generateUid(relativeUrl),
                    number = chapterNumber,
                    title = element.text(),
                    publicUrl = relativeUrl.toAbsoluteUrl(domain)
                )
            }.sortedByDescending { it.number }

        return manga.copy(
            title = title,
            description = description,
            chapters = chapters
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val url = chapter.publicUrl
        val doc = webClient.httpGet(url).parseHtml()

        return doc.body().select("img").mapNotNull { element ->
            val imageUrl = element.src() ?: return@mapNotNull null
            MangaPage(
                id = generateUid(imageUrl),
                url = imageUrl,
                pageNumber = 0 // Indexing handled by list order
            )
        }
    }
}
