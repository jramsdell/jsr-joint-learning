package learning.deep.tensorflow

import org.tensorflow.*
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicLong

class GraphBuilder(val g: Graph) {
    private val uid = AtomicLong(0)
    fun getName(name: String) = name + uid.incrementAndGet()


    fun<T> div(op1: Output<T>, op2: Output<T>) =
            binaryOp("Div", op1, op2)

    fun<T> mul(op1: Output<T>, op2: Output<T>) =
            binaryOp("Mul", op1, op2)

    fun<T> sub(op1: Output<T>, op2: Output<T>) =
            binaryOp("Sub", op1, op2)

    fun<T> add(op1: Output<T>, op2: Output<T>) =
            binaryOp("Add", op1, op2)


//    val zeroSum =   Tensor.create() {
//        Output reductionIndices =
//        g.opBuilder("Const", "ReductionIndices")
//            .setAttr("dtype", t.dataType())
//            .setAttr("value", t)
//            .build()
//            .output(0);


//    fun constant(name: String, value: Double) =
//            Tensor.create<Float>(value.toFloat(), Float::class.javaObjectType)
//                .run {
//                    g.opBuilder("Const", name)
//                        .setAttr("dtype", DataType.fromClass(Float::class.javaObjectType))
//                        .setAttr("value", this)
//                        .build()
//                        .output<Float>(0)
//                }

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

    fun constantTensor(name: String, values: FloatArray, shape: LongArray) =
            Tensor.create(shape, FloatBuffer.wrap(values))
                .let { t ->
                    val op = g.opBuilder("Const", name)
                        .setAttr("dtype", DataType.fromClass(Float::class.javaObjectType))
                        .setAttr("value", t)
//                        .setAttr("shape", longArrayOf(1, 1))
                        .build()
                        .output<Float>(0)
                    GraphElement(this, op)
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
            Tensor.create<Float>(value.toFloat(), Float::class.javaObjectType)

    fun tensor() =
            Tensor.create(floatArrayOf(1.0f, 1.0f))
                .run {
                    g.opBuilder("Const", "wlkqej")
                        .setAttr("value", this)
                        .setAttr("dtype", this.dataType())
                        .build()
                        .output<Float>(0)
                }


    fun tensor(shape: LongArray, values: FloatBuffer) =
            Tensor.create(shape, values)


    private fun<T> reduce(op: Output<T>, type: String): Output<Any> {
        val t = Tensor.create(intArrayOf(0))
        val reductionIndices = g.opBuilder("Const", "ReductionIndices" + uid.incrementAndGet().toString())
            .setAttr("dtype", t.dataType())
            .setAttr("value", t)
            .build()
            .output<Any>(0)

        return g.opBuilder(type, type + uid.incrementAndGet())
            .setAttr("T", DataType.FLOAT)
            .setAttr("Tidx", DataType.INT32)
            .addInput(op)
            .addInput(reductionIndices)
            .build()
            .output<Any>(0)!!

    }

    fun<T> sum(op: Output<T>): Output<Any> = reduce(op, "Sum")

    fun<T> exp(op: Output<T>) =
            g.opBuilder("Exp", "exp" + uid.incrementAndGet())
                .addInput(op)
                .build()
                .output<T>(0)

    fun<T> mean(op: Output<T>): Output<Any> = reduce(op, "Mean")


    fun<T> binaryOp(type: String, op1: Output<T>, op2: Output<T>): Output<T> =
            g.opBuilder(type, type + uid.incrementAndGet().toString())
                .addInput(op1)
                .addInput(op2)
                .build()
                .output<T>(0)

    fun unaryOpBuilder(type: String) =
            g.opBuilder(type, type + uid.incrementAndGet().toString())
}

