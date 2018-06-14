package learning.containers

import learning.LogitThingy
import org.nd4j.linalg.api.ndarray.INDArray
import utils.nd4j.kld
import utils.nd4j.*
import utils.nd4j.pow

enum class LogitDiff(val f: LogitThingy.(INDArray, INDArray) -> INDArray) {
    DIFF_SUB(f = LogitThingy::subTarget),
    DIFF_SUB_NEG(f = LogitThingy::subNeg),
    DIFF_KLD(f = LogitThingy::doKld),
}

private fun LogitThingy.subTarget(target: INDArray, pred: INDArray): INDArray {
    return target.sub(pred.transpose())!!
}

private fun LogitThingy.subNeg(target: INDArray, pred: INDArray): INDArray {
    val sub = target.sub(pred.transpose())!!
    return sub.pow(2.0) * sub.sign()
}


private fun LogitThingy.doKld(target: INDArray, pred: INDArray): INDArray {
    return target.kld(pred).mul(-1.0).broadcast(pred).transpose()
}
