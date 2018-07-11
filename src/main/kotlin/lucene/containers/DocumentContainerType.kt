package lucene.containers



sealed class IndexType(val typeName: String) {
    object PARAGRAPH : IndexType("PARAGRAPH")
    object ENTITY : IndexType("ENTITY")
    object SECTION : IndexType("SECTION")
//    TYPE_PARAGRAPH("PARAGRAPH"),
//    TYPE_ENTITY("ENTITY"),
//    TYPE_SECTION("SECTION")
}

