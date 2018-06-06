package learning

import koma.create
import koma.extensions.*
import koma.matrix.Matrix
import koma.rand
import koma.randn
import koma.vstack
import org.apache.commons.math3.distribution.NormalDistribution
import utils.misc.sharedRand


fun perturb(target: Matrix<Double>, nSamples: Int) {
    val norm = NormalDistribution(sharedRand, 1.0, 0.01)
//    println(target.nrows())
//    println(target.ncols())
    println(target.numRows())
    println(target.numCols())

    val results = (0 until nSamples).flatMap {
        val big = randn(1, target.numCols()) * 0.001 + target
        (big / big.elementSum()).toIterable()
    }
    val new = create(results.toDoubleArray(), nSamples, target.numCols())
    println(new.getCol(1).toIterable().toList())


//    val results = (0 until nSamples).flatMap {
//        val sample = Matrix.randn(target.nrows(), 1, 1.0, 0.001).add(target)
//        sample.div(sample.sum()).data().toList()
//    }
//    return results
}

