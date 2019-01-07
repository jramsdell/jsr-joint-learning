package learning.deep.tensorflow.extensions

//import org.bytedeco.javacpp.tensorflow
import learning.deep.tensorflow.GraphBuilder
import org.tensorflow.*
//import org.bytedeco.javacpp.tensorflow.*
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicLong
//import org.bytedeco.javacpp.tensorflow.TensorShape









//class GraphBuilder() {
//    val root: Scope = Scope.NewRootScope()
//
//    fun build(): GraphDef {
//        val graphDef = GraphDef()
//        val a = Const(root, Tensor.create(floatArrayOf(3f, 2f, -1f, 0f), TensorShape(2, 2)))
//        val x = Const(root.WithOpName("x"), Tensor.create(floatArrayOf(1f, 1f), TensorShape(2, 1)))
//
//        val y = MatMul(root.WithOpName("y"), Input(a), Input(x))
//        // y2 = y.^2
//        val y2 = Square(root, y.asInput())
//
//        // y2_sum = sum(y2)
//        val y2_sum = Sum(root, y2.asInput(), Input(0))
//
//        // y_norm = sqrt(y2_sum)
//        val y_norm = Sqrt(root, y2_sum.asInput())
//
//        // y_normalized = y ./ y_norm
//        Div(root.WithOpName("y_normalized"), y.asInput(), y_norm.asInput())
//
//
//
//
//        val status = root.ToGraphDef(graphDef)
//        if (!status.ok()) {
//            throw(Exception(status.error_message().string))
//        }
//        return graphDef
//    }
//
//}
//
//fun GraphDef.createSession(): Session {
//    val options = SessionOptions()
//    val session = Session(options)
//    val status = session.Create(this)
//    if (!status.ok()) {
//        throw(Exception(status.error_message().string))
//    }
//    return session
//}



//fun main(args: Array<String>) {
//    val graphBuilder: GraphBuilder = GraphBuilder()
//    val graphDef = graphBuilder.build()
//    val session = graphDef.createSession()
//}





fun main(args: Array<String>) {
//    val g = Graph()
//    val graphBuilder = GraphBuilder(g)
//    val x = graphBuilder.constant("x", 1.0)
//    val y = graphBuilder.constant("y", 1.0)
//    val z = graphBuilder.constant("z", 2.0)
//    val k = graphBuilder.placeholder<Float>("k", Float::class.javaObjectType)
//    val x2 = graphBuilder.constant("x2", 2.0)
//
//    val s1 = Shape.make(2, 2)
//    val zz1 = graphBuilder.placeholder<Float>("shapetest", Float::class.javaObjectType, Shape.make(2, 2))
//
//
//
//
//
//
//
//    val combined = graphBuilder.add(x, y).run { graphBuilder.mul(this, z) }
//    val huh = graphBuilder.add(zz1, combined)
//        .run { graphBuilder.sum(this) }
//        .run { graphBuilder.sum(this) }
//        .run { graphBuilder.exp(this) }
//    val sesssion = Session(g)
//
////    println(sesssion.runner().fetch(w).run().get(0).floatValue())
//
//    println(sesssion.runner().feed("shapetest", graphBuilder.tensor(longArrayOf(2, 2), FloatBuffer.wrap(floatArrayOf(2.0f, 2.0f, 2.0f, 2.0f)))).fetch(huh).run()[0].floatValue())
//

}

