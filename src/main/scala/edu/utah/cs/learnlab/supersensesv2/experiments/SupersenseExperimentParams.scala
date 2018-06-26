package edu.utah.cs.learnlab.supersensesv2.experiments

import edu.utah.cs.learnlab.core.features.FeatureExtractor
import edu.utah.cs.learnlab.experiments._
import edu.utah.cs.learnlab.supersensesv2._
import edu.utah.cs.learnlab.utilities.IOUtils
import edu.utah.cs.learnlab.wordstock.core._
import edu.utah.cs.learnlab.wordstock.io.AnnotationIO._
import java.io.File

trait SupersenseExperimentParams extends Experiment {

  val trainJSON = addParameter[String](name = "train-json-file",
    defaultValue = "",
    description = "The raw training file from the streusle data in JSON format.",
    optional = false)

  val devJSON = addParameter[String](name = "dev-json-file",
    defaultValue = "",
    description = "The raw dev file from the streusle data in JSON format.",
    optional = false)

  val testJSON = addParameter[String](name = "test-json-file",
    defaultValue = "",
    description = "The raw test file from the streusle data in JSON format.",
    optional = false)

  val outDir = addParameter[String](name = "preprocess-dir",
    description = "The directory where preprocessed files should be written.",
    defaultValue = "",
    optional = false)

  val hierarchyFile = addParameter[String](name = "hierarchy-file",
    description = "The json file with the preposition supersense hierarchy.",
    defaultValue = "",
    optional = false)


  def getFile(key: Parameter, params: ParameterSet): File = {
    val f = params[String](key)
    if(f.endsWith(".json")) {
      val ff = new File(f)
      if(!ff.exists())
        throw new Exception(s"File $f not found. Please check the path")
    } else {
      throw new Exception(s"Expecting a json file. Found $f instead")
    }
  }

  def preprocessOutputFile(dataFile: File, out: String) = {
    val name = dataFile.getName.replaceAll(".json", "")

    new File(out + File.separator + "preprocessed").mkdirs
    new File(out + File.separator + "preprocessed" + File.separator + name)
  }

  def featureFile(dataFile: File, out: String) = {
    val name = dataFile.getName.replaceAll(".json", "")
    new File(out + File.separator + "features").mkdirs
    new File(out + File.separator + "features" + File.separator + name)
  }

  def labelFeatureFile(label: String, dataFile: File, out: String) = {
    val name = dataFile.getName.replaceAll(".json", "")
    new File(out + File.separator + "features" + File.separator + name + "." + label)
  }


  def loadPreprocessed(file: File): Seq[Annotation] = {
    log info s"Loading preprocessed data for from $file"
    if(!file.exists())
      throw new Exception(s"File $file not found. Run preprocess first to generate this file.")

    IOUtils.readLines(file, gzip = false).map { line =>
      line.toAnnotation
    }
  }
  

}
