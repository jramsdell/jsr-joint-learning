package learning.deep.tensorflow

import learning.deep.tensorflow.components.GraphDataFlowComponent
import learning.graph.containers.GraphData
import org.tensorflow.*
import java.nio.DoubleBuffer
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicLong


class GraphBuilder(val g: Graph) {
    private val uid = AtomicLong(0)
    fun getName(name: String) = name + uid.incrementAndGet()

    val dataflow = GraphDataFlowComponent(this, g)



    fun constantFloat(name: String, value: Double) =
            Tensor.create<Float>(value.toFloat(), Float::class.javaObjectType)
                .let { t ->
                    val op = g.opBuilder("Const", name)
                        .setAttr("dtype", DataType.fromClass(Float::class.javaObjectType))
                        .setAttr("value", t)
                        .build()
                        .output<Float>(0)
                    GraphElement(this, op)
                }

    fun constant(name: String, value: Int) =
            Tensor.create<Int>(value, Int::class.javaObjectType)
                .let { t ->
                    val op = g.opBuilder("Const", name)
                        .setAttr("dtype", DataType.fromClass(Int::class.javaObjectType))
                        .setAttr("value", t)
                        .build()
                        .output<Int>(0)
                    GraphElement(this, op)
                }

    fun constant(name: String, value: Double) =
            Tensor.create<Double>(value, Double::class.javaObjectType)
                .let { t ->
                    val op = g.opBuilder("Const", name)
                        .setAttr("dtype", DataType.fromClass(Double::class.javaObjectType))
                        .setAttr("value", t)
                        .build()
                        .output<Double>(0)
                    GraphElement(this, op)
                }



    fun constantTensor(name: String, values: DoubleArray, shape: LongArray) =
            Tensor.create(shape, DoubleBuffer.wrap(values))
                .let { t ->
                    val op = g.opBuilder("Const", name)
                        .setAttr("dtype", DataType.fromClass(Double::class.javaObjectType))
                        .setAttr("value", t)
//                        .setAttr("shape", longArrayOf(1, 1))
                        .build()
                        .output<Double>(0)
                    GraphElement(this, op)
                }

    fun constantTensor(name: String, t: Tensor<Double>): Output<Double> {
        val op = g.opBuilder("Const", name)
            .setAttr("dtype", DataType.fromClass(Double::class.javaObjectType))
            .setAttr("value", t)
//                        .setAttr("shape", longArrayOf(1, 1))
            .build()
            .output<Double>(0)
        return op!!
    }


    fun<T> placeholder(name: String, classType: Class<*>) =
            g.opBuilder("Placeholder", name)
                .setAttr("dtype", DataType.fromClass(classType))
                .build()
                .output<T>(0)

    fun<T> placeholder(name: String, classType: Class<*>, shape: Shape) =
            g.opBuilder("Placeholder", name)
                .setAttr("dtype", DataType.fromClass(classType))
                .setAttr("shape", shape)
                .build()
                .output<T>(0)


    fun tensor(value: Double) =
            Tensor.create<Double>(value, Double::class.javaObjectType)

    fun tensor(value: Int) =
            Tensor.create<Int>(value, Int::class.javaObjectType)

    fun tensor() =
            Tensor.create(floatArrayOf(1.0f, 1.0f))
                .run {
                    g.opBuilder("Const", "wlkqej")
                        .setAttr("value", this)
                        .setAttr("dtype", this.dataType())
                        .build()
                        .output<Float>(0)
                }


    fun tensor(shape: LongArray, values: DoubleBuffer) =
            Tensor.create(shape, values)


    fun<T> tensorArray(values: Array<Output<T>>) =
            Tensor.create(values)


    fun<T> binaryOp(type: String, op1: Output<T>, op2: Output<T>): Output<T> =
            g.opBuilder(type, type + uid.incrementAndGet().toString())
                .addInput(op1)
                .addInput(op2)
                .build()
                .output<T>(0)

    fun opBuilder(type: String) =
            g.opBuilder(type, type + uid.incrementAndGet().toString())


}

