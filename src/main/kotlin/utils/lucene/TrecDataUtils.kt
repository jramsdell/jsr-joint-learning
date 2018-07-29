package utils.lucene

import co.nstant.`in`.cbor.model.*
import co.nstant.`in`.cbor.model.Array
import edu.unh.cs.treccar_v2.Data

private enum class DecodeDataType{
    UNICODE_ARRAY, BYTE_ARRAY
}

fun Data.Page.paragraphs() =
        flatSectionPathsParagraphs()
            .map { secParagraph -> secParagraph.paragraph  }

fun Data.Page.outlinks() =
        paragraphs()
            .flatMap(Data.Paragraph::getEntitiesOnly)
            .map { entity -> entity.replace(" ", "_").replace("%20", "_").replace("enwiki:", "") }
            .toSet()

fun Data.Page.filteredInlinks() =
        this.pageMetadata.inlinkIds.map { it.replace("enwiki:", "").replace("%20", "_").replace(" ","_") }

private val sectionsToIgnore = setOf(
        "See also", "References", "External links", "Further reading", "Notes"
)

fun getSections(cur: Data.Section): List<String> {
    if (cur.heading in sectionsToIgnore) return emptyList()
    val header = listOf(cur.heading)
    return  if (cur.childSections.isEmpty()) header
            else header + cur.childSections.flatMap { child -> getSections(child) }
}

private fun foldOverSection(f: (String, Data.Section, List<Data.Paragraph>) -> Unit, cur: Data.Section, path: String,
                            useFilter: Boolean = true) {
    if (cur.heading !in sectionsToIgnore) {
        val content = cur.children
            .filterIsInstance<Data.Para>()
            .map { it.paragraph }
            .filter { p -> !useFilter || (p.textOnly.length > 100 &&
                    !p.textOnly.contains(":") && !p.textOnly.contains("•")) }
        if (content.isNotEmpty())
            f(path, cur, content)
        cur.childSections.forEach { child -> foldOverSection(f, child, path + "/" + child.heading, useFilter) }
    }
}

@Suppress("UNCHECKED_CAST")
fun Data.Page.foldOverSection(useFilter: Boolean = true, f: (path: String, section: Data.Section, paragraphs: List<Data.Paragraph>) -> Unit) {
    val abstract = skeleton
        .takeWhile { it is Data.Para } as List<Data.Para>
    val pageSection = Data.Section(pageName, pageId, abstract)
    f(pageName, pageSection, abstract.map { it.paragraph } )
    childSections.forEach { section -> foldOverSection(f, section, pageName + "/" + section.heading, useFilter) }
}

fun Data.Page.getSectionLevels() =
    childSections.flatMap { child -> getSections(child) }


fun Data.PageMetadata.filteredCategoryNames() =
        categoryNames
            .map { name -> name.split(":")[1].replace(" ", "_") }

fun Data.Page.abstract() = paragraphs().first()


fun pageFromCbor(dataItem: DataItem): Data.Page {
    val array = (dataItem as Array).getDataItems()

    val pageName = array.get(1) as UnicodeString
    val pageId = array.get(2) as ByteString
    val skeletons = array.get(3)
    var pageType: Data.PageType = Data.PageType.Article
    var pageMetadata: Data.PageMetadata? = null
    if (array.size > 4) {
        pageType = pageTypeFromCbor(array.get(4))
        pageMetadata = pageMetadataFromCbor(array.get(5))// [ tag1, payload1, tag2, payload2, ...]
    }

    return Data.Page(pageName.string, String(pageId.getBytes()), pageSkeletonsFromCbor(skeletons), pageType, pageMetadata)
}

private fun pageTypeFromCbor(dataItem: DataItem): Data.PageType {
    val tag: DataItem = (dataItem as Array).dataItems.get(0)
    val tagValue: Int = (tag as UnsignedInteger).value.toInt()
    return Data.PageType.fromInt(tagValue)
}



private fun pageSkeletonsFromCbor(dataItem: DataItem): List<Data.PageSkeleton> {
    val skeletons = dataItem as Array
    return emptyList()
}

private fun pageSkeletonFromCbor(dataItem: DataItem): Data.PageSkeleton {
    val items = (dataItem as Array).dataItems
    val tagValue = (items[0] as UnsignedInteger).value.toInt()
    return when (tagValue) {
        0 -> {
            val heading = items[1] as UnicodeString
            val headingId = items[2] as ByteString
            Data.Section(heading.string, headingId.bytes.toString(), pageSkeletonsFromCbor(items[3]))
        }
        1 -> Data.Para(paragraphFromCbor(dataItem))
        2 -> imageFromCbor(items[1], items[2])
        3 -> listFromCbor(items[1], items[2])
        else -> throw RuntimeException("pageSkeletonFromCbor found an unhandled case: $items")
    }
}

private fun imageFromCbor(imageUrlDataItem: DataItem, skeletonDataItem: DataItem): Data.Image {
    val imageUrl = imageUrlDataItem as UnicodeString
    return Data.Image(imageUrl.string, pageSkeletonsFromCbor(skeletonDataItem))
}

private fun listFromCbor(nestingLevelItem: DataItem, paragraphItem: DataItem): Data.ListItem {
    val nestingLevel = nestingLevelItem as UnsignedInteger
    return Data.ListItem(nestingLevel.value.toInt(), paragraphFromCbor(paragraphItem))
}

private fun paragraphFromCbor(dataItem: DataItem): Data.Paragraph {
    val items = (dataItem as Array).dataItems
    val paraid = items.get(1) as ByteString
    val bodiesItem = items.get(2) as Array
    return Data.Paragraph(paraid.bytes.toString(), paraBodiesFromCbor(bodiesItem))
}


private fun paraBodiesFromCbor(dataItem: DataItem) =
        (dataItem as Array).dataItems
            .takeWhile { item: DataItem -> !Special.BREAK.equals(item) }
            .map(::paraBodyFromCbor)

private fun paraBodyFromCbor(dataItem: DataItem): Data.ParaBody {
    val items = (dataItem as Array).dataItems
    val tagId = (items.get(0) as UnsignedInteger).value.toInt()
    return when (tagId) {
        0 -> {
            val text = items.get(1) as UnicodeString
            Data.ParaText(text.toString())
        }
        1 -> {
            val subItems = (items.get(1) as Array).dataItems
            val page = subItems.get(1) as UnicodeString
            val pageId = subItems.get(3) as ByteString
            val anchorText = subItems.get(4) as UnicodeString
            val linkSectionMaybe = (subItems.get(2) as Array).dataItems
            if (linkSectionMaybe.isNotEmpty()) {
                val linkSection = linkSectionMaybe.get(0) as UnicodeString
                Data.ParaLink(page.string, pageId.bytes.toString(), linkSection.string, anchorText.string)
            } else {
                Data.ParaLink(page.string, pageId.bytes.toString(), anchorText.string)
            }
        }
        else -> throw RuntimeException("paraBodyFromCbor found an unhancled case: $items")
    }

}

private fun pageMetadataFromCbor(dataItem: DataItem): Data.PageMetadata {
    val outerArray = (dataItem as Array).dataItems
    val pageMetadata = Data.PageMetadata()

    outerArray.asSequence()
        .chunked(2)
        .takeWhile { (tagArray, _) ->  !Special.BREAK.equals(tagArray) }
        .forEach { (tagArray: DataItem, item: DataItem) ->
            val items = (item as Array).dataItems
            val tag: DataItem = (tagArray as Array).dataItems.get(0)
            val tagValue: Long = (tag as UnsignedInteger).value.toLong()

            when(tagValue) {
                0L -> getCodeArray(items, DecodeDataType.UNICODE_ARRAY)
                    .run(pageMetadata.redirectNames::addAll)
                1L -> getCodeArray(items, DecodeDataType.UNICODE_ARRAY)
                    .run(pageMetadata.disambiguationNames::addAll)
                2L -> getCodeArray(items, DecodeDataType.BYTE_ARRAY)
                    .run(pageMetadata.disambiguationIds::addAll)
                3L -> getCodeArray(items, DecodeDataType.UNICODE_ARRAY)
                    .run(pageMetadata.categoryNames::addAll)
                4L -> getCodeArray(items, DecodeDataType.BYTE_ARRAY)
                    .run(pageMetadata.categoryIds::addAll)
                5L -> getCodeArray(items, DecodeDataType.BYTE_ARRAY)
                    .run(pageMetadata.inlinkIds::addAll)
                6L -> getCodeArray(items, DecodeDataType.UNICODE_ARRAY)
                    .map { name: String -> Data.ItemWithFrequency<String>(name, 1)}
                    .run(pageMetadata.inlinkAnchors::addAll)
            }
        }
    return pageMetadata
}

private fun getCodeArray(resultArray: List<DataItem>, dataType: DecodeDataType): List<String> =
    resultArray
        .takeWhile { item -> !Special.BREAK.equals(item) }
        .map { item ->
            when(dataType) {
                DecodeDataType.UNICODE_ARRAY -> (item as UnicodeString).string
                DecodeDataType.BYTE_ARRAY -> (item as ByteString).bytes.toString()
            }
        }

