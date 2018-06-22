package edu.utah.cs.learnlab.supersensesv2

import edu.utah.cs.learnlab.core.features.FeatureExtractor
import edu.utah.cs.learnlab.supersensesv2.data.SSTReader
import edu.utah.cs.learnlab.wordstock.core.{Constituent, ViewName}
import edu.utah.cs.learnlab.wordstock.features.WordFeatures.EnglishSpecificWordFeatures._
import edu.utah.cs.learnlab.wordstock.features.{ConstituentFeatureExtractors, DictionaryFeatures, WordFeatures}
import edu.utah.cs.learnlab.wordstock.utilities.Navigators._

/**
  * @author Oliver Richardson
  * @author Vivek Srikumar
  */
case class Features(hasNE: Boolean = true) {

  // We are using the POS and lemma from the streusle data. These
  // are read into the gold POS and lemma views by the SST reader.
  val posView = ViewName.GoldPOS
  val lemmaView = ViewName.GoldLemma  

  val wn = DictionaryFeatures.WordNetFeatures(lemmaView, posView)

  /**
    * Base features:
    * 1. Word, part of speech, capitalziaiton indicators
    * 2. Conflated partof speach (coarser POS tag)
    * 3. Indicator for existence in WordNet
    * 4. WordNet synsets for first and all senses
    * 5. WordNet lemma, lexicographer file names, part, member, substance holonyms
    * 6. Roget Thesaurus divisions for the word
    * 7. First two, last three letters.
    * 8. Indicators for known afixes
    * 9. Named entity label
    */

  val base: FeatureExtractor[Constituent] = {
    val f = Seq(WordFeatures.word,
      WordFeatures.lowerCase,
      WordFeatures.pos(posView),
      WordFeatures.capitalizationIndicator,
      WordFeatures.coarsePOS(posView),
      wn.existsEntry,
      wn.synsetsFirstSense,
      wn.synsetsAllSenses,
      wn.lemma,
      wn.lexFileAll, wn.lexFileFirstSense,
      wn.partHolonymsAll, wn.memberHolonymsAll, wn.substanceHolonymsAll,
      wn.partHolonymsFirstSense, wn.memberHolonymsFirstSense, wn.substanceHolonymsFirstSense,
      DictionaryFeatures.rogetThesaurus,
      WordFeatures.prefixes(2), WordFeatures.suffixes(3),
      deAdjectivalAbstractNounSuffixes,
      deNominalNounProducingSuffixes,
      deVerbalSuffixes,
      temporalPrefixes,
      quantityPrefixes,
      spatialPrefixes,
      gerundSuffix).reduce(_ ++ _)

    if(hasNE) f ++ ConstituentFeatureExtractors.neLabel(ViewName.StanfordNE)
    else f
  }

  def govFeatures(f: FeatureExtractor[Constituent]): FeatureExtractor[Constituent] = {
    f.compose("gov") { c =>

      // by convention, the governor (generated externally) has an
      // incoming edge into the preposition. This is set in the SST
      // reader
      assert(c.viewName == SSTReader.prepositions)
      val s = c.incomingRelations.head.source
      if (s.tokens.isEmpty) None
      else Some(s)
    }
  }

  def complementFeatures(f: FeatureExtractor[Constituent]): FeatureExtractor[Constituent] = {
    f.compose("obj") { c =>

      // by convention, the complement (generated externally) has an
      // outgoing edge from the preposition. This is set in the SST
      // reader
      assert(c.viewName == SSTReader.prepositions)
      val s = c.outgoingRelations.head.target
      if (s.tokens.isEmpty) None
      else Some(s)
    }
  }

  def prevNFeatures(f: FeatureExtractor[Constituent]): FeatureExtractor[Constituent] = {
    f.compose("prevN")(c => c.previousNoun(posView))
  }

  def prevVFeatures(f: FeatureExtractor[Constituent]): FeatureExtractor[Constituent] = {
    f.compose("prevV")(c => c.previousVerb(posView))
  }

  def prevJFeatures(f: FeatureExtractor[Constituent]): FeatureExtractor[Constituent] = {
    f.compose("prevJ")(c => c.previousAdjective(posView))
  }

  def nextNFeatures(f: FeatureExtractor[Constituent]): FeatureExtractor[Constituent] = {
    f.compose("nextN")(c => c.nextNoun(posView))
  }

  def contextWindowFeatures(f: FeatureExtractor[Constituent], length: Int = 2): FeatureExtractor[Constituent] = {
    ConstituentFeatureExtractors.context(length, includePosition = false)(f)
  }

  val fex: FeatureExtractor[Constituent] = {
    val depContext = govFeatures(base) ++ complementFeatures(base)
    val linearContext = prevNFeatures(base) ++ prevVFeatures(base) ++ prevJFeatures(base) ++ nextNFeatures(base)
    val neighborhoodFeatures = base ++ depContext ++ linearContext ++ contextWindowFeatures(base, 2)

    // This is the same as the easy adapt trick --
    // [neighborhoodFeatures, neighborhoodFeatures &
    // prepositionLemma] the first part captures preposition
    // independent statistics while the second part captures
    // preposition dependent statistics
    neighborhoodFeatures & (FeatureExtractor.bias ++ WordFeatures.lemma(lemmaView))
  }

}

object Features {

  def makeFeatureExtractor(noNEFeatures: Boolean): FeatureExtractor[Constituent] = {
    Features(!noNEFeatures).fex
  }
}
