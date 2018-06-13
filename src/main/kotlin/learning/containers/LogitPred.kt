package learning.containers

import learning.LogitThingy
import org.nd4j.linalg.api.ndarray.INDArray
import utils.nd4j.*

enum class LogitPred(val f: LogitThingy.(INDArray, INDArray) -> INDArray) {
    PRED_DOT(f = LogitThingy::dotProduct),
    PRED_DOT_SIGMOID(f = LogitThingy::dotProductSigmoid),
}

private fun LogitThingy.dotProduct(features: INDArray, params: INDArray): INDArray {
    return features.mmul(params).transpose()
}

private fun LogitThingy.dotProductSigmoid(features: INDArray, params: INDArray): INDArray {
    return features.mmul(params).sigmoid().transpose()
}



