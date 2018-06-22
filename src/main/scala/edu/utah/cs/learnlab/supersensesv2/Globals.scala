package edu.utah.cs.learnlab.supersensesv2

import java.io.File

import edu.utah.cs.learnlab.logging.HasLogging
import edu.utah.cs.learnlab.supersensesv2.data.SSTReader
import edu.utah.cs.learnlab.wordstock.core.{Annotation, Constituent}
import edu.utah.cs.learnlab.wordstock.utilities.Navigators._
import edu.utah.cs.learnlab.utilities.IOUtils


/**
  * @author Vivek Srikumar
  */
object Globals extends HasLogging {
  def prepositionConstituent(c: Constituent): Option[Constituent] = {
    val prepView = c.annotation(SSTReader.prepositions)
    val preps = prepView.constituents.filter(p => p.label == "Preposition")

    preps.find(p => p.span == c.span)

  }

  @inline def prepositionConstituents(a: Annotation): IndexedSeq[Constituent] = {

    @inline def isPreposition(c: Constituent) = {
      val hasPrepositionTag = Set("P", "PP", "INF.P", "POSS", "PRON.POSS").contains(c.label)

      val validSceneLabel = c.labelInView(SSTReader.sceneView) match {
        case Some(l) => !(l == "??" || l == "`$")
        case None => true
      }

      val validFunctionLabel = c.labelInView(SSTReader.functionView) match {
        case Some(l) => !(l == "??" || l == "`$")
        case None => true
      }

      hasPrepositionTag && validFunctionLabel && validSceneLabel

    }

    a(SSTReader.lexCatViewName).constituents.filter(c => isPreposition(c)).flatMap(prepositionConstituent)

  }

  @inline def prepositionConstituents(as: Seq[Annotation]): Seq[Constituent] =
    as.flatMap(prepositionConstituents)

  val nullLabel = "p.X"
}
