package other.composition

import de.lab4inf.math.functions.Polynomial
import edu.jas.arith.PrimeTest
import org.matheclipse.core.expression.F.*
import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.exception.MathArithmeticException
import org.apache.commons.math3.fraction.BigFraction
import org.apache.commons.math3.fraction.BigFractionFormat
import org.matheclipse.core.eval.ExprEvaluator
import org.matheclipse.core.eval.EvalEngine
import org.matheclipse.core.expression.F
import org.matheclipse.core.polynomials.PolynomialsUtils
import utils.misc.identity
import utils.stats.countDuplicates
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.absoluteValue
import kotlin.math.atan
import kotlin.math.atan2


class RPolynomial( val x: BigFraction, val c: BigFraction, var symbol: String = "", var history: List<RPolynomial> = emptyList() ) {
    val root: BigFraction = (c * -1) / if (x.numeratorAsInt == 0) 1.toFrac() else x
    val invariant: BigFraction = try { (c / (x - 1)) * -1 } catch (e: MathArithmeticException) { BigFraction(0) }


    infix fun o(other: RPolynomial): RPolynomial =
            RPolynomial( x = other.x * x, c = c * other.x + other.c,
                    history = if (history.isEmpty()) listOf(this, other) else history + listOf(other)
            )



    fun distInv(): BigFraction = (invariant + root)
    fun distInvBase(): BigFraction = (root - invariant)
    fun distInv(other: RPolynomial): BigFraction = (root + other.invariant) / other.x
    fun distInvBase(other: RPolynomial): BigFraction = (root + other.invariant)

    fun getIntermediates(hist: List<RPolynomial>, rMap: Boolean = false): ArrayList<RPolynomial> {
        val intermediates = ArrayList<RPolynomial>()
        var cur = hist.first()
        hist.drop(1)
            .forEach { next ->
                cur = if (!rMap) cur o next else next o cur
                intermediates.add(cur)
            }

        return intermediates

    }

    fun getIntermediatesR(hist: List<RPolynomial>, rMap: Boolean = false): BigFraction? {
//        var cur = getIntermediates(hist, rMap)
        var cur = if (rMap) hist.chunked(2).flatMap { it.reversed() } else hist
        return cur.map { (it.root * it.invariant) }
//            .chunked(2)
//            .map { chunk ->
//                if (chunk.size == 1) chunk.first() else chunk[0] - chunk[1] }
            .reduce { acc, bigFraction -> acc + bigFraction  }
//        return cur.map { (it.root - it.invariant)/2 }.reduce { acc, bigFraction -> acc + bigFraction  }

    }


    fun angleInv(): Double = atan2(root.toDouble(), invariant.toDouble())

    fun angle() = atan2(c.toDouble(),  x.toDouble())
    fun angle(other: RPolynomial): Double {
        val dX = x.toDouble() - other.x.toDouble()
        val dY = c.toDouble() - other.c.toDouble()
        return atan2(dY, dX)
    }

    fun inverse(): RPolynomial =
        RPolynomial(x = x.reciprocal(), c = c * x.reciprocal() * -1)

    override fun toString(): String = "${x}x".replace(" ", "") +
            "+" + "$c".replace(" ", "") + if (symbol != "") " ($symbol)" else ""

//    fun equals(other: RPolynomial): Boolean {
//        return x == other.x && c == other.c
//    }

    override fun equals(other: Any?): Boolean {
        val maybePolynomial = other as? RPolynomial
        return if (maybePolynomial != null) { maybePolynomial.x == x && maybePolynomial.c == c }
        else super.equals(other)
    }

    override fun hashCode(): Int {
        return Pair(x, c).hashCode()
    }

    operator fun invoke(frac: BigFraction) = x * frac + c
    operator fun invoke(long: Long) = x * long + c
    operator fun invoke(double: Double) = x.toDouble() * double + c.toDouble()

    operator fun minus(frac: BigFraction): RPolynomial = RPolynomial( x = x, c = c - frac)
    operator fun minus(poly: RPolynomial): RPolynomial = RPolynomial( x = x - poly.x, c = c - poly.c)
    operator fun plus(poly: RPolynomial): RPolynomial = RPolynomial( x = x + poly.x, c = c + poly.c)
    operator fun plus(frac: BigFraction): RPolynomial = RPolynomial( x = x, c = c + frac)
    operator fun times(frac: BigFraction): RPolynomial = RPolynomial( x = x * frac, c = c * frac)
    operator fun div(frac: BigFraction): RPolynomial = RPolynomial( x = x / frac, c = c / frac)

    companion object {
        fun intRPoly(x: Int, c: Int, symbol: String = ""): RPolynomial {
            val p1 = RPolynomial(x = x.toFrac(), c = c.toFrac(), symbol = symbol)
//            return RPolynomial(x = x.toFrac(), c = c.toFrac(), history = listOf(p1), symbol = symbol)
            return p1
        }

        fun createFormatter(): PrintFormatter {
            val headers = listOf(
                    "poly", "root", "inv", "distInv", "distInvBase", "add", "mul", "p1d", "p1dbase", "p2d", "p2dbase"
            )
            return PrintFormatter(headers)
        }
    }

}


operator fun BigFraction.plus(other: BigFraction) = this.add(other)
operator fun BigFraction.plus(other: Long) = this.add(other)
operator fun BigFraction.plus(other: Double) = this.add(other.toFrac())

operator fun BigFraction.minus(other: BigFraction) = this.subtract(other)
operator fun BigFraction.minus(other: Long) = this.subtract(other)
operator fun BigFraction.minus(other: Double) = this.subtract(other.toFrac())

operator fun BigFraction.times(other: BigFraction) = this.multiply(other)
operator fun BigFraction.times(other: Long) = this.multiply(other)
operator fun BigFraction.times(other: Double) = this.multiply(other.toFrac())

operator fun BigFraction.div(other: BigFraction) = this.divide(other)
operator fun BigFraction.div(other: Long) = this.divide(other)
operator fun BigFraction.div(other: Double) = this.divide(other.toFrac())


fun Double.toFrac(): BigFraction = BigFraction(this, 0.00001, 100)
fun Int.toFrac(): BigFraction = BigFraction(this, 1)

/**
 * For p1 being composed many times before being composed with p2: p1 o p2, p1 o p1 o p2, p1 o p1 o p1 o p2, etc.
 * Let p1 = x_1 + c_1, p2 = x_2 + c_2
 *      (p1_inv * p2_x) * (p1_x^n - 1) + p2_c
 *
 */

fun findCommutants(generators: List<RPolynomial>) {
    var curGeneration = generators

//    val monoid = HashMap<BigFraction, ArrayList<RPolynomial>>()
    val monoid = HashMap<RPolynomial, ArrayList<RPolynomial>>()
    val seen = HashMap<RPolynomial, AtomicInteger>()
    generators.forEach {
//        seen.getOrPut(it, { AtomicInteger() }).incrementAndGet()
        seen.getOrPut(it, { AtomicInteger() }).incrementAndGet()
//        monoid.computeIfAbsent(it.invariant, { ArrayList() }).add(it)
        monoid.computeIfAbsent(it, { ArrayList() }).add(it)
    }

    (0 until 5).forEach {
        curGeneration =
                curGeneration.flatMap { child -> generators.map { gen -> child o gen } }
//                    .map { (child, generator) -> child o generator }
//                    .filter { newChild ->
//                        seen.getOrPut(newChild, { AtomicInteger() }).incrementAndGet() == 1
//                    }
                    .onEach { newChild ->
                        seen.getOrPut(newChild, { AtomicInteger() }).incrementAndGet()
                        val inv = newChild.invariant
    //                        monoid.computeIfAbsent(inv, { ArrayList() }).add(newChild) }
                            monoid.computeIfAbsent(newChild, { ArrayList() }).add(newChild) }
    }

//    seen
//        .filter { it.value.get() > 1 }
//        .forEach { (k,v) ->
//        println("$k:$v")
//    }

    println("")
    monoid
//        .filter { it.value.size > 1 }
        .entries.sortedByDescending { it.value.minBy { it.history.size }!!.history.size }
        .forEach { (k,v) ->

            val histories =
                    v
//                        .filter { seen[it]!!.get() > 1 || it.history.size == 1 }
//                        .map { it.history to it }
//                        .countDuplicates()
//                        .map { it.key.second }
                        .sortedByDescending { it.toString() }
                        .map { "\n\t" + it.toString() + it.history.toString() +
                                "\n\t\t" + it.getIntermediatesR(it.history).toString()  +
                                "\n\t\t" + it.getIntermediatesR(it.history, true).toString()  +
                                "\n\t\t" + it.getIntermediatesR(it.history.reversed()).toString() +
                                "\n\t\t" + it.getIntermediatesR(it.history.reversed(), true).toString()
                        }
            if (v.size > 0 && histories.isNotEmpty()) {
                println("$k: $histories")
            }
    }
}

fun generatePolynomialFactors(): HashMap<Int, ArrayList<RPolynomial>> {
    val factors = HashMap<Int, ArrayList<RPolynomial>>()

    (2 until 100).forEach { factor ->
        val list = ArrayList<RPolynomial>()
        factors[factor] = ArrayList()
        (1 .. (factor    ) ).forEach { c ->
            list.add(RPolynomial.intRPoly(factor, c))
        }
        factors[factor] = list
    }

    return factors
}

fun getFactors(number: Int): List<List<Int>> {
    val factors = (2 until 300).filter { number.rem(it) == 0 }.dropLast(1)
    return findCombos(number, emptyList(), factors)
}

fun findCombos(target: Int, cur: List<Int>, remaining: List<Int> ): List<List<Int>> {
    val curValue = cur.fold(1, Int::times)
    return if (curValue > target) emptyList()
    else if (curValue == target ) listOf(cur)
    else remaining.flatMap { choice -> findCombos(target, cur + listOf(choice), remaining) }
}

fun findPolyCombos(cur: RPolynomial, remaining: List<ArrayList<RPolynomial>>): List<RPolynomial> {
    return if (cur.x.toDouble() == 1.0 && cur.c.toDouble() == 0.0) listOf(cur)
    else if (cur.x.toDouble() > 1.0 || cur.c.toDouble() > 0.0 || remaining.isEmpty()) emptyList()
    else {
        val curPolies = remaining.first()
        val remainingPolies = remaining.drop(1)
        curPolies.flatMap { poly -> findPolyCombos(cur o poly, remainingPolies) }
    }
}

fun decomposePolynomial(poly: RPolynomial, polyFactors: HashMap<Int, ArrayList<RPolynomial>>) {
    val target = poly.inverse()
    val factors = getFactors(poly.x.toInt())
    println(poly.x.toInt())
    println(factors)

    val wee = factors.map {
        it.map { factor -> polyFactors[factor]!! }}

    val result = wee.flatMap { c -> findPolyCombos(target, c) }
    val huh = result.map { p -> p.history.drop(1) }
        .sortedByDescending { it.first().toString() }
//        .map { it to it.fold(BigFraction(0)) { acc, poly -> acc + poly.root} }
//        .map { it to it.windowed(2).map { (it[0].root - it[1].root) + (it[0].invariant - it[1].invariant) }.reduce(BigFraction::add) }
        .map { it to it.windowed(2).map { (it[0].root - it[1].root)  }.reduce(BigFraction::add) }
        .sortedByDescending { it.second }
        .onEach { println("${it.second} : ${it.first}") }

    huh.map { it.second }
        .groupBy { it.denominator }
//        .filter { it.value.size == 1 }
//        .onEach { (k, v) -> println("${k.s()} : ${v.reduce(BigFraction::add).s()}") }
        .onEach { (k, v) -> println("${k} : ${v.reduce(BigFraction::add).s()}") }
        .map { it.value.reduce(BigFraction::add) }
        .reduce(BigFraction::add)
        .let { r -> println(r.s())
        }
//    println(huh.sumBy { it.second })

}

fun BigFraction.s(): String = toString().replace(" ", "")

fun main(args: Array<String>) {
    val p1 = RPolynomial.intRPoly(3, 1)
    val p2 = RPolynomial.intRPoly(3, 2)
    val p3 = RPolynomial.intRPoly(2, 3, symbol = "C")

    val generators = listOf(
            p1, p2
//            (p1 o p2).apply { symbol = "A"; history = listOf(this) },
//            (p2 o p1).apply { symbol = "B"; history = listOf(this) },
//            p3.apply { history = listOf(this) }
    )
//
//    findCommutants(generators)
    val polyFactors = generatePolynomialFactors()
    decomposePolynomial(RPolynomial.intRPoly(34,  9), polyFactors)
//    println(getFactors(133))
//    println((p2 o p1).invariant - (p1 o p2).invariant)


//    pf.add(p1 + p2)
//    pf.add(p1 o p1 o p1)
//    pf.add(p1 o p1 o p1 o p1)
//    pf.add(p2 o p2)
//    pf.add(p2 o p2 o p2)
//    pf.add(p1 o p2)
//    pf.add(p1 o p1 o p2)
//    pf.add(p1 o p1 o p1 o p2)
//    var cur = p2
//    pf.add(cur)
//    (0 until 5).forEach {
//        cur = cur o p2
//        pf.add(cur)
//    }
//    pf.print()
}


