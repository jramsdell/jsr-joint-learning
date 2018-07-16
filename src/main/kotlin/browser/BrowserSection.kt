package browser


class BrowserSection(val heading: String, val paragraphs: List<BrowserParagraph>) {
    override fun toString(): String {
        val text = paragraphs.map(BrowserParagraph::toString)
            .joinToString("<br>\n") + "<br>\n"

        return "<h1>$heading</h1>\n$text"
    }

}
