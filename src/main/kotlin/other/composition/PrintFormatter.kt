package other.composition



class PrintFormatter(val headers: List<String>) {
    val columns = headers.map { header -> ArrayList<String>().apply { add(header) } }

    fun addRow(row: List<String>) =
        row.zip(columns).forEach { (element, column) ->  column.add(element) }

    fun print(spacing: Int = 5) {
        val sizes = columns.map { column -> column.maxBy { element -> element.length }!!.length + spacing }

//        val headerResult = headers
//            .mapIndexed { index, header -> header + spaces(sizes[index] - header.length)  }
//            .joinToString("")

//        println(headerResult)

        (0 until columns.first().size).forEach {  rIndex ->
            val result = columns
                .mapIndexed { cIndex, col -> col[rIndex] + spaces(sizes[cIndex] - col[rIndex].length)  }

            println(result.joinToString(""))
        }
    }

    fun spaces(n: Int) = (0 until n).map { " " }.joinToString("")

    fun add(poly: RPolynomial) {
        val elements = ArrayList<String>()

        val p1 = RPolynomial.intRPoly(2, 5)
        val p2 = RPolynomial.intRPoly(4, 2)

        elements.add(poly.toString())
        elements.add(poly.root.toString().replace(" ", ""))
        elements.add(poly.invariant.toString().replace(" ", ""))
        elements.add(poly.distInv().toString().replace(" ", ""))
        elements.add(poly.distInvBase().toString().replace(" ", ""))
        elements.add("${poly.c + poly.x}")
        elements.add("${poly.c - poly.x}")
        elements.add(poly.distInv(p1).toString().replace(" ", ""))
        elements.add(poly.distInvBase(p1).toString().replace(" ", ""))
//        elements.add(p1.distInv(poly).toString().replace(" ", ""))
//        elements.add(p1.distInvBase(poly).toString().replace(" ", ""))
//        elements.add(poly.distInv(p2).toString().replace(" ", ""))
//        elements.add(poly.distInvBase(p2).toString().replace(" ", ""))
        elements.add(poly.distInv(p2).toString().replace(" ", ""))
        elements.add(poly.distInvBase(p2).toString().replace(" ", ""))
        addRow(elements)
    }
}

fun Double.format(digits: Int) = java.lang.String.format("%.${digits}f", this)