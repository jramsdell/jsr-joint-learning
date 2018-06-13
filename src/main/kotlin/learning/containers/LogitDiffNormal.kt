package learning.containers

import learning.LogitThingy
import org.nd4j.linalg.api.ndarray.INDArray
import utils.nd4j.*

enum class LogitDiffNormal(val f: LogitThingy.(INDArray) -> INDArray) {
    DMOD_SIGMOID(f = LogitThingy::dmodSigmoid),
    DMOD_SIGMOID_NORMAL(f = LogitThingy::dmodSigmoidNormal),
    DMOD_EXP(f = LogitThingy::dmodExp),
    DMOD_EXP_NORMAL(f = LogitThingy::dmodExpNormal),
    DMOD_NONE(f = LogitThingy::noNormalization)
}

private fun LogitThingy.dmodSigmoid(diff: INDArray): INDArray =
        diff.sigmoid() * diff.sign()

private fun LogitThingy.dmodSigmoidNormal(diff: INDArray): INDArray =
        diff.sigmoid().normalizeRows().mul(diff.sign())

private fun LogitThingy.dmodExp(diff: INDArray): INDArray =
//    diff.exp() * diff.sign()
diff.exp() * diff.sign()

private fun LogitThingy.dmodExpNormal(diff: INDArray): INDArray =
//    diff.exp().normalizeColumns().mul(diff.sign())
diff.exp().normalizeRows().mul(diff.sign())

private fun LogitThingy.noNormalization(diff: INDArray): INDArray = diff
