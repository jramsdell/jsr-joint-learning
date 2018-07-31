package other.composition
//
//import org.matheclipse.core.expression.F.*
//import org.apache.commons.math3.complex.Complex
//import org.matheclipse.core.eval.ExprEvaluator
//import org.matheclipse.core.eval.EvalEngine
//import org.matheclipse.core.expression.F
//import org.matheclipse.core.polynomials.PolynomialsUtils
////import org.matheclipse.core.expression.F
//import other.composition.extensions.*
//import java.lang.System.exit
//import java.util.logging.Logger
//
//
//fun inverted(x: Complex) =
//    ((x * 2.0 - 1.0).sqrt() * -1.0 - 1.0 ) * (1/2.0)
//
//
//fun wee() {
////    val  p = PolynomialFunction(doubleArrayOf(1.0, 2.0))
////    val solver = LaguerreSolver()
////    var root = solver.solve(100, p, 0.0)
////    println(root)
//
//    val util = MyEvaluator(true, 1)
//    EvalEngine.get().isPackageMode = true
//
//    with (util) {
//        val f = 2 * x + 1
//        val fac = symbol("fac")
////        val iterated = iteratedFunction(x, 1.0, 2) { prev -> 2 * prev + 1}
////        val result = eval( iterated o 2)
////        eval(SetDelayed(function(fac, x_), 2 * function(fac, x - 1)) + 1)
////        eval(Set(function(fac, ZZ(0)), ZZ(1)))
////        val result = eval(function(fac, ZZ(10)))
////        println(result)
//
////        x assign 2 * a + 1
//        val myfunc = iteratedFunction(x, num(1.0)) { prev -> 2 * prev + 1 }
////        println(myfunc(2))
////        val myfunc2 = iteratedFunction(x, myfunc(1)) { prev -> 3 * prev + 1 }
////        val huh = eval("2x^2 + 2x * 1")
////        println(eval("NestList[2# + 1 &, 10, 20]"))
////        println(eval("NestList[2# + 1 &, 10, 20]"))
//        val backtick = parse("#")
//        val myexpr = "1/4(-1 - sqrt(8# - 7))  &"
//
////        println(eval(NestList.of(1 + backtick, ZZ(10), ZZ(5))))
//        println(eval("Nest[${myexpr}, 1.0, 100]"))
//
////        eval(Set(fac(0), ZZ(1)))
////        println(eval(fac(10)))
//
////        x assign eval(f)
////        x assign eval(f)
////        x assign eval(f)
////        x assign eval(f)
////        println(eval(x))
//
//    }
//
//}
//
//
//
//
//operator fun Complex.times(x: Double) = this.multiply(Complex(x, 0.0))
//operator fun Complex.div(x: Double) = this.divide(x)
//operator fun Complex.plus(x: Double) = this.add(x)
//operator fun Complex.minus(x: Double) = this.subtract(x)
//
//operator fun Double.plus(x: Complex) = Complex(this, 0.0).add(x)
//
//
//fun main(args: Array<String>) {
//    wee()
//    exit(0)
////    var res = Complex(2.0, 1.0)
////    (0 until 100).forEach {
////        res = inverted(res)
////    }
//}