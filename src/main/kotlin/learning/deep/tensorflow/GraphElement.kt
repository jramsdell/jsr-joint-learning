package learning.deep.tensorflow

import org.tensorflow.*
import utils.misc.toArrayList

data class GraphElement<T>(private val builder: GraphBuilder, val op: Output<T>) {

    operator fun div(other: GraphElement<T>) =
            GraphElement(builder = builder, op = builder.binaryOp("Div", this.op, other.op))

    operator fun plus(other: GraphElement<T>) =
            GraphElement(builder = builder, op = builder.binaryOp("Add", this.op, other.op))

    operator fun minus(other: GraphElement<T>) =
            GraphElement(builder = builder, op = builder.binaryOp("Sub", this.op, other.op))

    operator fun times(other: GraphElement<T>) =
            GraphElement(builder = builder, op = builder.binaryOp("Mul", this.op, other.op))

    fun asString() =
            builder.opBuilder("AsString")
                .addInput(this.op)
                .build()
                .output<String>(0)
                .run { GraphElement(builder, this) }

    fun print() =
            builder.opBuilder("Print")
                .addInput(this.op)
                .addInputList(arrayOf(this.op))
                .build()
                .output<Any>(0)
                .run { GraphElement(builder, this) }

    fun pow(base: Double) =
            builder.opBuilder("Pow")
                .addInput(this.op)
                .addInput(builder.constantFloat("pow", base).op)
                .build()
                .output<T>(0)
                .let { newOp -> GraphElement(builder, newOp) }

    fun softMax(): GraphElement<T> {
        val exped = exp()
        val total = exped.sumElement()
        return exped.div(total)
    }

    private fun rollUnaryOperator(type: String) =
            builder.opBuilder(type)
                .addInput(this.op)
                .build()
                .output<T>(0)
                .let { newOp -> GraphElement(builder, newOp) }


    fun log() = rollUnaryOperator("Log")
    fun square() = rollUnaryOperator("Square")
    fun sqrt() = rollUnaryOperator("Sqrt")
    fun neg() = rollUnaryOperator("Neg")
    fun reciprocal() = rollUnaryOperator("Reciprocal")



    fun exp() =
            builder.g.opBuilder("Exp", builder.getName("Exp"))
                .addInput(op)
                .build()
                .output<T>(0)
                .let { newOp ->
                    GraphElement(builder, newOp)
                }

    private fun<T> reduce(op: Output<T>, type: String, index: Int = 0): Output<T> {
        val t = Tensor.create(intArrayOf(index))
        val reductionIndices = builder.g.opBuilder("Const", builder.getName("indices"))
            .setAttr("dtype", t.dataType())
            .setAttr("value", t)
            .build()
            .output<Any>(0)

        return builder.g.opBuilder(type, builder.getName(type))
            .setAttr("T", DataType.DOUBLE)
            .setAttr("Tidx", DataType.INT32)
            .addInput(op)
            .addInput(reductionIndices)
            .build()
            .output<T>(0)!!

    }

    fun sum(index: Int = 0) = GraphElement(builder, reduce(op, "Sum", index))
    fun mean(index: Int = 0) = GraphElement(builder, reduce(op, "Mean", index))

    fun lift() = builder.opBuilder("Pack")
        .addInputList(arrayOf(op))
        .setAttr("axis", 1)
        .build()
        .output<Double>(0)
        .let { newOp -> GraphElement(builder, newOp) }

    fun sumElement(): GraphElement<T> {
        val nTimes = this.op.shape().numDimensions()
        var curElement = this
        (0 until nTimes).forEach {
            curElement = curElement.sum()
        }
        return curElement
    }

    fun variableAssign(value: Output<Double>) =
                builder.opBuilder("Assign")
                    .addInput(this.op)
                    .addInput(value)
                    .build()
                    .output<Double>(0)
                    .run { GraphElement(builder, this) }

}

fun Tensor<Double>.toDoubleArray(): Array<DoubleArray> {
    val dim = this.shape()
    val a = (0 until 2).map { (0 until 2).map { 0.0 }.toDoubleArray()}.toTypedArray()
    this.expect(Double::class.javaObjectType).copyTo(a)
    return a
}

fun main(args: Array<String>) {
    val g = Graph()
    val builder = GraphBuilder(g)

    val e1 = builder.constantFloat("W1", 2.0)
    val e2 = builder.constantFloat("W2", 2.0)
    val final = (e1 + e2).pow(2.5)
        .sqrt()

//    val woo = builder.constantTensor("abc", floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f), longArrayOf(2, 2))
//    val woo2 = builder.constantTensor("abc2", floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f), longArrayOf(2, 2))
//    val tensorArray = arrayOf(woo, woo2)
//
//    val woo3 = (woo + woo2).softMax().sum()
//    val woo4 = (woo + woo2).softMax().sumElement()
//    val tArray = builder.tensorArray(arrayOf(woo.op, woo2.op))
//
//
//    val sesssion = Session(g)
////    println(sesssion.runner().fetch(woo.op).run().get(0).floatValue())
////    println(sesssion.runner().fetch(woo3.op).run().get(0).expect(Float::class.javaObjectType).toFloatArray().toList().map { it.toList() })
//    println(sesssion.runner().fetch(woo4.op).run().get(0).expect(Float::class.javaObjectType).floatValue())
}

