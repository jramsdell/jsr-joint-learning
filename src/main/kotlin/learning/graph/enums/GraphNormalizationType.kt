package learning.graph.enums

import utils.stats.defaultWhenNotFinite
import kotlin.math.pow

enum class GraphNormalizationType(val f: (Double) -> Double) {
    G_STANDARD(f = { weight -> (1.0/weight) }),
    G_POW(f = { weight -> (1.0/weight.pow(2.0)) }),
    G_EXP(f = { weight -> (Math.exp(-weight)) }),
    G_LOG(f = { weight -> (1.0 / Math.log(weight)) })
}