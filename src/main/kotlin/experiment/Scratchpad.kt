package experiment

import entity.EntityDatabase


fun testEntityRetrieval() {
    val db = EntityDatabase("entity_index_2/")
    println(db.getEntity("Chocolate"))
//    println(db.doSearch("Chocolate can have medicinal properties"))

}

fun main(args: Array<String>) {
    testEntityRetrieval()
}