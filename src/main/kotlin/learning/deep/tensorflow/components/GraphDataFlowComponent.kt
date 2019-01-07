package learning.deep.tensorflow.components

import learning.deep.tensorflow.GraphBuilder
import learning.deep.tensorflow.GraphElement
//import learning.deep.tensorflow.TensorArray
import learning.deep.tensorflow.TensorArray
import learning.deep.tensorflow.toDoubleArray
import org.tensorflow.*
import org.tensorflow.op.Scope
import java.nio.ByteBuffer
import java.nio.DoubleBuffer

data class GraphDataFlowComponent(private val builder: GraphBuilder, private val g: Graph) {

    fun<T> tensorArray(size: Int, classType: Class<*>, shape: Shape) =
            builder.opBuilder("TensorArrayV3")
                .addInput(builder.constant("size", size).op)
                .setAttr("dtype", DataType.fromClass(classType))
                .setAttr("shape", shape)
                .build()
                .output<T>(0)



    fun tensorArray(size: Int, classType: Class<*>): TensorArray<*> {
        val baseOut = builder.opBuilder("TensorArrayV3")
            .addInput(builder.constant("size", size).op)
            .setAttr("dtype", DataType.fromClass(classType))
            .setAttr("element_shape", Shape.make(2, 2))
            .build()

        val op = baseOut.output<Any>(0)
        val flow = baseOut.output<Float>(1)
//        val operand = Operand<Int>({ builder.constant("qwe", 2).op })
//        val meArray = TensorArray.create(Scope(g), operand, Double::class.javaObjectType)
        return TensorArray<Any>(op, arrayListOf(2), flow)
    }

}


fun main(args: Array<String>) {
    val graph = Graph()
    val builder = GraphBuilder(graph)
    val a = builder.dataflow.tensorArray(2, Double::class.javaObjectType)
    val t1 = builder.tensor(longArrayOf(2, 2), DoubleBuffer.wrap(doubleArrayOf(2.0, 2.0, 1.0, 1.0)))
    val t2 = builder.tensor(longArrayOf(2, 2), DoubleBuffer.wrap(doubleArrayOf(1.0, 1.0, 1.0, 1.0)))
    val t3 = builder.constantTensor("aslskdjakd", doubleArrayOf(
//            1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0
            0.1, 0.2, 0.3, 0.4, 1.0, 2.0, 3.0, 4.0
    ), longArrayOf(2, 2, 2))

    val t4 = builder.constantTensor("aslskdjakdaa", doubleArrayOf(
            1.0, 1.0
    ), longArrayOf(2, 1, 1))
//    ), longArrayOf(2))

//    val wee = Tensor.create(listOf(t1, t2))

    val final = builder.opBuilder("TensorArrayWriteV3")
        .addInput(a.op)
        .addInput(builder.constant("index", 0).op)
//        .addInput(builder.constantTensor("Weeqw", floatArrayOf(2.0, 2.0, 1.0, 1.0), Shape(2)).op)
        .addInput(builder.constantTensor("www", t1))
        .addInput(a.flow)
        .build()
        .output<Float>(0)

    val x = builder.placeholder<Double>("x", classType = Double::class.javaObjectType)

    val t1Const = builder.constantTensor(builder.getName("www"), t1)
        .run {
            (GraphElement(builder, this) + GraphElement(builder, x))
                .op
        }
    val t2Const = builder.constantTensor(builder.getName("www"), t2)


    val final2 = builder.opBuilder("TensorArrayWriteV3")
        .addInput(a.op)
        .addInput(builder.constant(builder.getName("index"), 1).op)
//        .addInput(builder.constantTensor("Weeqw", floatArrayOf(2.0, 2.0, 1.0, 1.0), Shape(2)).op)
        .addInput(builder.constantTensor(builder.getName("www"), t2))
        .addInput(a.flow)
        .build()
        .output<Float>(0)

    val final3 = builder.opBuilder("TensorArrayReadV3")
        .addInput(a.op)
        .addInput(builder.constant(builder.getName("index"), 1).op)
        .addInput(a.flow)
        .setAttr("dtype", DataType.DOUBLE)
        .build()
        .output<Double>(0)


    val added = builder.opBuilder("AddN")
        .addInputList(arrayOf(t1Const, t2Const))
        .build()
        .output<Any>(0)
        .run { GraphElement(builder, this) }
        .sumElement()

//    val hubba = GraphElement(builder, a.op)
//        .sum()


//    println(Session(graph).runner().fetch(added.op).run()[0].doubleValue())
//    println(Session(graph).runner()
//        .feed("x", builder.tensor(1.0))
//        .fetch(added.op)
//        .run()[0].doubleValue())

    val computation = (t4 * t3)
        .sum(0)

//    println(Session(graph).runner().fetch(computation.op).run()[0].doubleValue())
    println(Session(graph).runner().fetch(computation.op)
        .run()[0]
        .expect(Double::class.javaObjectType)
        .toDoubleArray()
        .map { it.toList() }.toList()
    )


}
