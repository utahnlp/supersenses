package edu.utah.cs.learnlab.supersensesv2.experiments

import java.io.File

import edu.utah.cs.learnlab.experiments._
import edu.utah.cs.learnlab.supersensesv2.data._
import edu.utah.cs.learnlab.utilities.IOUtils
import edu.utah.cs.learnlab.wordstock.annotators.StanfordAnnotator
import edu.utah.cs.learnlab.wordstock.core.Constituent
import edu.utah.cs.learnlab.wordstock.io.AnnotationIO._

/**
  * @author Vivek Srikumar
  */
object PreprocessExperiment extends Experiment with SupersenseExperimentParams {
  
  val name = "preprocess"
  val description = "Read STREUSLE data and run preprocessing tools on it. Currently, this adds named entities from the Stanford NER."

  override def run(expdir: String, params: ParameterSet) = {
    log info s"Preprocessing data."

    val trainFile = getFile(trainJSON, params)
    val devFile = getFile(devJSON, params)
    val testFile = getFile(testJSON, params)

    val out = params[String](outDir)
    new File(out).mkdirs

    for(dataFile <- Seq(trainFile, devFile, testFile)) {
      log.info(s"Processing $dataFile")

      val preprocessFile = preprocessOutputFile(dataFile, out)

      log info s"Writing output to $preprocessFile"

      IOUtils.writeLines(preprocessFile, gzip = false) {
        new SSTReader(name, dataFile).iterator.map { a =>
          a += StanfordAnnotator.ne
          a.toJson
        }.toSeq
      }
    }
  }
}
