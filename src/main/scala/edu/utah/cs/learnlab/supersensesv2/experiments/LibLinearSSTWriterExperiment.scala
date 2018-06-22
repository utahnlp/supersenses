package edu.utah.cs.learnlab.supersensesv2.experiments

import edu.utah.cs.learnlab.core.features.Lexicon
import edu.utah.cs.learnlab.experiments._
import edu.utah.cs.learnlab.supersensesv2.Globals._
import edu.utah.cs.learnlab.supersensesv2._
import edu.utah.cs.learnlab.supersensesv2.data._
import edu.utah.cs.learnlab.utilities.IOUtils
import edu.utah.cs.learnlab.wordstock.core._
import java.io.File
import scala.io.Source

/**
  * @author Vivek Srikumar
  */
object LibLinearSSTWriterExperiment extends Experiment with SupersenseExperimentParams {

  val name = "write-json"
  val description = "Convert liblinear prediction files into json formatted output for evaluation using the offical scripts"

  val scenePredictionsFile = addParameter[String](name = "scene-predictions",
    defaultValue = "",
    optional = false,
    description = "A file containing one scene prediction per line",
    valueName = "<file>")

  val functionPredictionsFile = addParameter[String](name = "function-predictions",
    defaultValue = "",
    optional = false,
    description = "A file containing one prediction per line",
    valueName = "<file>")

  val outFile = addParameter[String](name = "output",
    defaultValue = "",
    optional = false,
    description = "The output json file",
    valueName = "<file>")

  final case class Prediction(scene: Int, function: Int)

  val sceneView = ViewName("Scene", StandardAnnotators.Utah)
  val functionView = ViewName("Function", StandardAnnotators.Utah)

  def run(experimentDir: String, params: ParameterSet) = {

    val outputFile = params[String](outFile)

    val llex: Lexicon = SupersenseHierarchy(new File(params[String](hierarchyFile))).labelLexicon

    val predictions = loadPredictedLabels(params)
    val preps = loadPredictionKeys(params)

    assert(predictions.size == preps.size, s"Found ${predictions.size} predictions and ${preps.size} preps")

    val mappedPredictions = preps.zip(predictions).toMap

    val out = new SSTWriter(new File(outputFile),
      sceneView, functionView)

    val outputDirectory = params[String](outDir)
    val testSet = preprocessOutputFile(getFile(testJSON, params), outputDirectory)


    def isPrep(c: Constituent) = {
      Set("P", "PP", "INF.P", "POSS", "PRON.POSS").contains(c.label)
    }

    loadPreprocessed(testSet).foreach { a =>
      val constituents = a(SSTReader.lexCatViewName).constituents.filter(isPrep)

      val pairs = constituents.flatMap { c =>
        predict(llex, c, mappedPredictions).map { case (s, f) =>
          (c.span -> s, c.span -> f)
        }

      }
      val (scenes, functions) = pairs.unzip

      a += new SpanLabelView(sceneView, a, scenes)
      a += new SpanLabelView(functionView, a, functions)
      out.write(a)

    }

    out.close()
  }


  def predict(llex: Lexicon,
              c: Constituent,
              mappedPredictions: Map[(String, Span, String), Prediction]): Option[(String, String)] = {
    val text = c.annotation.rawText
    val span = c.span
    val prep = c.rawText.toLowerCase

    val key = (text, span, prep)
    mappedPredictions.get(key) map { prediction =>
      val scene = llex(prediction.scene).get
      val function = llex(prediction.function).get

      (scene, function)
    }
  }


  def loadPredictionKeys(params: ParameterSet): Seq[(String, Span, String)] = {

    val out = params[String](outDir)
    log info s"Loading data from $out"

    val testFile = getFile(testJSON, params)
    val testInfoFile = featureFile(testFile, out)

    val lines = IOUtils.readLines(testInfoFile, gzip = false).iterator
    lines.next().toInt
    lines.next().toInt

    lines.map { line =>

      val parts = line.split("\t").toIterator

      val text = parts.next
      val spanSpec = parts.next.split(",")
      val start = spanSpec(0).toInt
      val end = spanSpec(1).toInt
      val span = Span(start, end)
      val prep = parts.next

      (text, span, prep)
    }.toSeq
  }

  def loadPredictedLabels(params: ParameterSet): Seq[Prediction] = {
    val functionPredictionIds = loadPredictionIds(params, functionPredictionsFile)
    val scenePredictionIds = loadPredictionIds(params, scenePredictionsFile)

    assert(scenePredictionIds.size == functionPredictionIds.size)
    scenePredictionIds.zip(functionPredictionIds).toSeq.map(pair => Prediction(pair._1, pair._2))
  }

  def loadPredictionIds(params: ParameterSet, param: Parameter): Seq[Int] = {
    val file = new File(params[String](param))

    if(!file.exists()) {
      throw new Exception(s"File $file not found. Have you trained the classifier using liblinear?")
    }

    Source.fromFile(file).getLines.map { line =>
      line.toInt - 1
    }.toSeq
  }
}
