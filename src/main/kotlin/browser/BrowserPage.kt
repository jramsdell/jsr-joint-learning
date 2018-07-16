package browser

import java.io.File


class BrowserPage(val name: String, val sections: List<BrowserSection>) {
    override fun toString(): String {
        val base = "Your search results for: $name\n"
        val text = sections.map(BrowserSection::toString)
            .joinToString("<br>\n")
        return base + text
    }

    fun write(out: String) {
        val f = File(out).writeText(this.toString())
    }

}