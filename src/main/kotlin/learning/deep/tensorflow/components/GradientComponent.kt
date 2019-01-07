package learning.deep.tensorflow.components

import learning.deep.tensorflow.GraphBuilder
import learning.deep.tensorflow.GraphElement
import org.tensorflow.Graph
import org.tensorflow.Operand
import org.tensorflow.Session
import org.tensorflow.op.Scope
import org.tensorflow.op.core.Gradients
import java.nio.DoubleBuffer

data class GradientComponent(private val builder: GraphBuilder, private val g: Graph) {
    private val scope =  Scope(g)
    fun sipleGradient(f: Operand<Double>, parameters: MutableIterable<Operand<Double>>, dx: Gradients.Options) =
        Gradients.create(scope, f, parameters, dx)

}

fun main(args: Array<String>) {
    val g = Graph()
    val builder = GraphBuilder(g)

    val myArr = builder.tensor(longArrayOf(2, 2), doubleArrayOf(1.0, 2.0, 3.0, 4.0).run { DoubleBuffer.wrap(this) })
    val c = builder.constantTensor("qwlkej", myArr)
        .run { GraphElement<Double>(builder, this) }


    val other = builder.constantTensor("Qwlej", doubleArrayOf(0.0, 0.0), longArrayOf(2, 1))
    val other2 = builder.constantTensor("Qwlejhqwe", doubleArrayOf(0.1), longArrayOf(1))
    val f = (c * other).sumElement()

//    val grad = builder.gradients.sipleGradient(f.op, arrayListOf(other.op), Gradients.dx(arrayListOf(other2.op)))
//        .dy()
//        .run { GraphElement(builder, this).print().op }


//    val result = Session(g).runner().fetch(f.op).run()[0].doubleValue()
//    val l = arrayListOf(0.0, 0.0).toArray()
//    val result = Session(g).runner().fetch(grad.dy()[0]).run()[0].copyTo(l)
//    val result = Session(g).runner().fetch(grad).run()[0].toString()
//    println(result)


}
