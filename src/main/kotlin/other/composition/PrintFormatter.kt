package other.composition



class PrintFormatter(val headers: List<String>) {
    val columns = headers.map { _ -> ArrayList<String>() }

    fun addRow(row: List<String>) =
        row.zip(columns).forEach { (element, column) ->  column.add(element) }

    fun print(spacing: Int = 5) {
        val sizes = columns.map { column -> column.maxBy { element -> element.length }!!.length + spacing }

        val headerResult = headers
            .mapIndexed { index, header -> header + spaces(sizes[index] - header.length)  }
            .joinToString("")

        println(headerResult)

        (0 until columns.first().size).forEach {  rIndex ->
            val result = columns
                .mapIndexed { cIndex, col -> col[rIndex] + spaces(sizes[cIndex] - col[rIndex].length)  }

            println(result.joinToString(""))
        }
    }

    fun spaces(n: Int) = (0 until n).map { " " }.joinToString("")

    fun add(poly: RPolynomial) {
        val elements = ArrayList<String>()

        elements.add(poly.toString())
        elements.add(poly.root.toString().replace(" ", ""))
        elements.add(poly.invariant.toString().replace(" ", ""))
        elements.add(poly.distInv().toString().replace(" ", ""))
        elements.add(poly.angle().toString())
        elements.add(poly.angleInv().toString())
        addRow(elements)
    }
}