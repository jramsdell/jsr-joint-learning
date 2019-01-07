package learning.deep.tensorflow

import learning.deep.tensorflow.interfaces.OpHasDimensions
import learning.deep.tensorflow.interfaces.OpHasFlow
import org.tensorflow.Output


sealed class Operation<T>(val op: Output<T>)

class TensorArray<T>(op: Output<T>, override val dimensions: ArrayList<Long> = arrayListOf(0L),
                     override val flow: Output<Float>) :
        Operation<T>(op), OpHasDimensions, OpHasFlow

fun<T> hmm(w: T) where T : OpHasDimensions, T : Number {

}

fun main(args: Array<String>) {
}