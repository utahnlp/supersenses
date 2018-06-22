package edu.utah.cs.learnlab.supersensesv2.experiments

import java.io._

import edu.utah.cs.learnlab.core.algebra.SparseVector
import edu.utah.cs.learnlab.core.features._
import edu.utah.cs.learnlab.experiments._
import edu.utah.cs.learnlab.supersensesv2._
import edu.utah.cs.learnlab.supersensesv2.data._
import edu.utah.cs.learnlab.wordstock.core.{Annotation, Constituent}
import edu.utah.cs.learnlab.wordstock.utilities.Navigators._

/**
  * @author Vivek Srikumar
  */
object PreExtractExperiment extends Experiment with SupersenseExperimentParams {

  val name = "extract"
  val description = "Extract features from the data and convert into two sets of files (one for scene and the other for function). Additionally, this command also creates a label key file that keeps track of example identifiers. The label key file helps reconstruct the output in the json format after liblinear is done with it."

  val featuresToDiscard = addParameter[Int](shortCommandLineOption = 'd',
    name = "feature-discard-count",
    description = "Number of occurances of a feature under which it is discarded",
    defaultValue = 2,
    valueName = "<count>",
    optional = true)

  val noNEFeatures = addParameter[Boolean](name = "without-ne",
    defaultValue = false,
    description = "Ignore named entity features",
    optional = true)
  

  def run(experimentDir: String, params: ParameterSet) = {
    log info "Extracting features and storing liblinear formatted outputs"
    val out = params[String](outDir)
    new File(out).mkdirs

    val trainFile = getFile(trainJSON, params)
    val devFile = getFile(devJSON, params)

    val train = loadPreprocessed(preprocessOutputFile(trainFile, out))
    val dev = loadPreprocessed(preprocessOutputFile(devFile, out))

    val lex = makeFeatureLexicon(train, dev, params)

    val llex = SupersenseHierarchy(new File(params[String](hierarchyFile))).labelLexicon

    extract(out, trainFile, train, lex, llex, params)

    extract(out, devFile, dev, lex, llex, params)

    val testFile = getFile(testJSON, params)
    val test = loadPreprocessed(preprocessOutputFile(testFile, out))
    extract(out, testFile, test, lex, llex, params)
  }


  def makeFeatureLexicon(train: Seq[Annotation], dev: Seq[Annotation], params: ParameterSet) = {
    val n = params[Int](this.featuresToDiscard)
    val fex = Features.makeFeatureExtractor(params[Boolean](noNEFeatures))
    log info s"Using features ${fex.toString}"

    if (n > 1) {
      log info s"Counting features and pruning out the ones that occur fewer than $n times"
      val cl = new CountingLexicon()

      countFeatures(train, fex, cl)
      countFeatures(dev, fex, cl)

      val lex = cl.pruneByCount(n)
      lex.lockLexicon
      lex
    } else {
      val lex = new Lexicon()
      withTimer("populating feature lexicon") {
        Globals.prepositionConstituents(train ++ dev).foreach { s =>
          fex(s).keys.foreach(k => lex(k))
        }
      }
      lex.lockLexicon()
      lex
    }
  }

  def countFeatures(as: Seq[Annotation], fex: FeatureExtractor[Constituent], cl: CountingLexicon) = {
    Globals.prepositionConstituents(as).foreach { s =>
      val features = fex(s)
      features.keys.foreach(cl.count)
    }
  }

  def findLabelIdFromName(label: String, llex: Lexicon): Int = {
    llex(label) match {
      case Some(lblId) => lblId
      case None =>
        throw new Exception(s"Label $label not found")
    }
  }

  def extract(outDir: String, dataFile: File,
    as: Seq[Annotation],
    lex: Lexicon, llex: Lexicon, params: ParameterSet) = {

    val fex = Features.makeFeatureExtractor(params[Boolean](noNEFeatures))    

    log info s"Preextracting features for prepositions in $dataFile"

    val outFile = featureFile(dataFile, outDir)
    log info s"Saving pre-extracted features to outFile"

    val soutFile = labelFeatureFile("scene", dataFile, outDir)
    log info s"Saving lib-linear formatted file for scene labels to $soutFile"

    val foutFile = labelFeatureFile("function", dataFile, outDir)
    log info s"Saving lib-linear formatted file for function labels to $foutFile"

    val out = new PrintWriter(outFile)
    val sout = new PrintWriter(soutFile)
    val fout = new PrintWriter(foutFile)

    val numFeatures = lex.size
    val numLabels = llex.size
    out.println(numFeatures)
    out.println(numLabels)

    var i = 0
    for (c <- Globals.prepositionConstituents(as)) {

      val a = c.annotation.rawText
      val span = c.span.start + "," + c.span.end

      val prep = c.rawText.toLowerCase

      // hack to account for the fact that the preposition spans may
      // come from gold or auto identification. if the data is a
      // train/dev example, we should ignore any null labels. if
      // not, then we should put a dummy label for them.

      val maybeFunction = c.labelInView(SSTReader.functionView)
      val maybeScene = c.labelInView(SSTReader.sceneView)

      val (scId, fnId) =  (maybeFunction, maybeScene) match {
        case (Some(fn), Some(sc)) =>
          val sId: Int = findLabelIdFromName(sc, llex)
          val fId: Int = findLabelIdFromName(fn, llex)
          (sId, fId)
        case (Some(fn), None) =>
          log.error("Found function but not scene!!")
          log.error(s"Text = $a")
          log.error(s"Span = ${c.span}")
          log.error(s"Preposition = $prep")
          log.error(s"Scene = $fn")
          log.error(s"Lexcat = ${c.labelInView(SSTReader.lexCatViewName)}")

          throw new Exception("Scene not found!")
        case _ => (0, 0)
      }

      val feats: SparseVector = fex(c, lex)
      val featString: String = feats.keys.sorted.map(k => (k +1) + ":" + feats(k)).mkString(" ")

      val output = Seq(a, span, prep, scId + "," + fnId).mkString("\t")
      out.println(output)

      sout.println(s"${scId + 1} $featString")
      fout.println(s"${fnId + 1} $featString")

      i = i + 1
      if (i % 1000 == 0) log.info(i + " examples done")

    }
    out.close()
    sout.close()
    fout.close()
  }
}
