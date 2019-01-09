package lucene.joint

import features.compatability.CompatabilityFeatureType
import features.compatability.GramContainer
import learning.deep.tensorflow.GraphElement
import org.tensorflow.Output
import org.tensorflow.Shape
import java.nio.DoubleBuffer


data class JointTensorflowContainer(
        val nEntityFeatures: Int,
        val nCompatFeatures: Int,
        val entityWeightTensor: Output<Double>,
        val compatWeightTensor: Output<Double>,
        val lossFunction: Output<Double>,
        val gramFeatures: List<GramContainer<CompatabilityFeatureType.ENTITY_TO_PARAGRAPH>>
) {
    companion object {
        fun construct(jointRunner: JointRunner) = with(jointRunner) {
            val gramFeatures = retrieveCompatabilityMaps()
            addEntityFeatures()
            val nGramFeatures = gramFeatures.first().matrices.size.toLong()
            val nEntityFeatures = queryContainers.first().entities.first().features.size.toLong()
            val relevantPassages = createRelevantPassageTensors()


            // Need to initialize weight variables
            var entityWeights = createWeightPlaceholder("weightEntity", Shape.make(nEntityFeatures, 1))
                .run { GraphElement(builder, this) }
            var compatWeights = createWeightPlaceholder("weightCompat", Shape.make(nGramFeatures, 1, 1))
                .run { GraphElement(builder, this) }

            val fakeEntityWeights = (0 until nEntityFeatures)
                .map { 1.0 }
                .run { DoubleBuffer.wrap(this.toDoubleArray()) }
            val fakeCompatWeights = (0 until nGramFeatures)
                .map { 1.0 }
                .run { DoubleBuffer.wrap(this.toDoubleArray()) }

            val boundEntityWeight = builder.tensor(longArrayOf(nEntityFeatures, 1), fakeEntityWeights)
                .run { builder.constantTensor("t1", this) }
            val boundCompatWeight = builder.tensor(longArrayOf(nGramFeatures, 1, 1), fakeCompatWeights)
                .run { builder.constantTensor("t2", this) }

            entityWeights = entityWeights.variableAssign(boundEntityWeight)
            compatWeights = compatWeights.variableAssign(boundCompatWeight)

            val compatTensors = convertCompatabilityMapsToTensor(gramFeatures, compatWeights)
            val entityTensors = convertEntityFeatures(entityWeights)


            // Construct loss function
            val pushForward = entityTensors.zip(compatTensors)
                .map { (entity, compat) -> (entity * compat).sum().log() }
//                .map { (entity, compat) -> (compat * entity).sum(0) }
//                .mapIndexed { index, gE ->
//                    if (index == 0)
//                        (gE.print() as GraphElement<Double>)
//                    else gE
//                }
                .zip(relevantPassages)
                .map { (computation, rel) -> (rel * computation).sumElement().op }
                .toTypedArray()

            val lossFunction = builder.addN(pushForward)

            JointTensorflowContainer(
                    nEntityFeatures = nEntityFeatures.toInt(),
                    nCompatFeatures = nGramFeatures.toInt(),
                    compatWeightTensor = boundCompatWeight,
                    entityWeightTensor = boundEntityWeight,
                    lossFunction = lossFunction.op,
                    gramFeatures = gramFeatures)
        }
    }
}