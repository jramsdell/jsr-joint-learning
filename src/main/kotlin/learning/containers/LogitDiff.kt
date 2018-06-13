package learning.containers

import learning.LogitThingy
import org.nd4j.linalg.api.ndarray.INDArray
import utils.nd4j.kld

enum class LogitDiff(val f: LogitThingy.(INDArray, INDArray) -> INDArray) {
    DIFF_SUB(f = LogitThingy::subTarget),
    DIFF_KLD(f = LogitThingy::doKld),
}

private fun LogitThingy.subTarget(target: INDArray, pred: INDArray): INDArray {
    return target.sub(pred.transpose())!!
}

private fun LogitThingy.doKld(target: INDArray, pred: INDArray): INDArray {
    return target.kld(pred)
}
