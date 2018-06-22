package edu.utah.cs.learnlab.supersensesv2

import edu.utah.cs.learnlab.experiments._
import edu.utah.cs.learnlab.logging._
import edu.utah.cs.learnlab.supersensesv2.experiments._

/**
  * @author Vivek Srikumar
  */
object Main extends ExperimentEntryPoint {
  def allExperiments = Seq(
    () => PreprocessExperiment,
    () => PreExtractExperiment,
    () => LibLinearSSTWriterExperiment
  )

  override def loggerFactory(dir: String) = List(ConsoleLogger)
}
