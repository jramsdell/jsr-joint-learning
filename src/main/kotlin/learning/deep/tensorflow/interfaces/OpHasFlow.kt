package learning.deep.tensorflow.interfaces

import org.tensorflow.Output

interface OpHasFlow {
    val flow: Output<Float>
}