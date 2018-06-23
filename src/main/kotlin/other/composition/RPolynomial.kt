package other.composition

import org.matheclipse.core.expression.F.*
import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.fraction.BigFraction
import org.apache.commons.math3.fraction.BigFractionFormat
import org.matheclipse.core.eval.ExprEvaluator
import org.matheclipse.core.eval.EvalEngine
import org.matheclipse.core.expression.F
import org.matheclipse.core.polynomials.PolynomialsUtils
import kotlin.math.absoluteValue
import kotlin.math.atan
import kotlin.math.atan2


class RPolynomial( val x: BigFraction, val c: BigFraction ) {
    val root: BigFraction = (c * -1) / x
    val invariant: BigFraction = c / (x - 1)


    infix fun o(other: RPolynomial): RPolynomial =
            RPolynomial( x = other.x * x, c = c * other.x + other.c)


    fun distInv(): BigFraction = x * (root - invariant)
    fun distInv(other: RPolynomial): BigFraction = x * (root - other.invariant)

    fun angleInv(): Double = atan2(root.toDouble(), invariant.toDouble())

    fun angle() = atan2(c.toDouble(),  x.toDouble())
    fun angle(other: RPolynomial): Double {
        val dX = x.toDouble() - other.x.toDouble()
        val dY = c.toDouble() - other.c.toDouble()
        return atan2(dY, dX)
    }

    fun stats(useHeader: Boolean = false): String {
        val poly = this.toString()
        val angle = angle()
        val distInv = distInv().toString().replace(" ", "")
        val inv = invariant.toString().replace(" ", "")
        val rt = root.toString().replace(" ", "")

        val header = "poly\troot\tinv\tdistInv\tangle\tangleInv\n"
        val formatted = "($poly)\t$rt\t$inv\t$distInv\t${angle()}\t${angleInv()}"

        return if (useHeader) header + formatted else formatted
    }

    override fun toString(): String = "${x}x + $c"

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
        fun intRPoly(x: Int, c: Int) = RPolynomial(x = x.toFrac(), c = c.toFrac())

        fun createFormatter(): PrintFormatter {
            val headers = listOf(
                    "poly", "root", "inv", "distInv", "angle", "angleInv"
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


fun main(args: Array<String>) {
    val p1 = RPolynomial.intRPoly(2, 1)
    val p2 = RPolynomial.intRPoly(3, 1)
    val composed = p1 o p2

    val pf = RPolynomial.createFormatter()
    pf.add(p1)
    pf.add(p2)
    pf.add(composed)
    pf.print()
}

