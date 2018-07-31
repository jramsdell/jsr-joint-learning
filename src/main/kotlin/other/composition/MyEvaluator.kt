package other.composition
//
//
//import org.matheclipse.core.expression.F.*
//import org.apache.commons.math3.complex.Complex
//import org.matheclipse.core.eval.ExprEvaluator
//import org.matheclipse.core.eval.EvalEngine
//import org.matheclipse.core.expression.F
//import org.matheclipse.core.interfaces.IAST
//import org.matheclipse.core.interfaces.IExpr
//import org.matheclipse.core.interfaces.ISymbol
//import org.matheclipse.core.polynomials.PolynomialsUtils
////import org.matheclipse.core.expression.F
//import other.composition.extensions.*
//
//
//class MyEvaluator(outlistDisabled: Boolean, historyCapacity: Int) : ExprEvaluator(outlistDisabled, historyCapacity) {
//    var iterCounter = 0
//
//    fun clear(iSymbol: ISymbol) {
//        eval(Clear(iSymbol))
//    }
//
//    fun iteratedFunction(variable: ISymbol, init: IExpr, f: (IAST) -> IExpr): ISymbol {
//        val func = symbol("iter${iterCounter++}")
//        val p = pattern(variable)
//        val previousStep =  function(func, variable - 1)
//
//        eval(SetDelayed(function(func, p), f(previousStep)))
//        eval( Set( function(func, ZZ(0)), init ) )
//
//        return func!!
//    }
//
//
//    fun roots(e: IExpr) {
//        Roots(ZZ(1), ZZ(1))
//        F.List()
//    }
//}