package utils.lucene

import edu.unh.cs.treccar_v2.Data

fun Data.Page.paragraphs() =
        flatSectionPathsParagraphs()
            .map { secParagraph -> secParagraph.paragraph  }

fun Data.Page.outlinks() =
        paragraphs()
            .flatMap(Data.Paragraph::getEntitiesOnly)
            .map { entity -> entity.replace(" ", "_") }
            .toSet()


fun Data.PageMetadata.filteredCategoryNames() =
        categoryNames
            .map { name -> name.split(":")[1].replace(" ", "_") }

fun Data.Page.abstract() = paragraphs().first()


