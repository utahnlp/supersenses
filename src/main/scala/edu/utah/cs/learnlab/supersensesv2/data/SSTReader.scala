package edu.utah.cs.learnlab.supersensesv2.data

import java.io.File

import edu.utah.cs.learnlab.logging.HasLogging
import edu.utah.cs.learnlab.utilities.IOUtils
import edu.utah.cs.learnlab.wordstock.core._
import edu.utah.cs.learnlab.wordstock.datasets.AnnotationReader
import org.json4s.native.JsonMethods._
import org.json4s.{DefaultFormats, _}

import scala.collection.immutable.Iterable


/**
  * @author Vivek Srikumar
  */
class SSTReader(val name: String, val dataFileName: File) extends AnnotationReader with HasLogging {
  private implicit val formats = DefaultFormats

  val data: Seq[Annotation] = readSourceFromFile(dataFileName)

  def iterator: Iterator[Annotation] = data.iterator

  def extract[T](toks: List[JValue], field: String)
                (implicit formats: Formats, mf: scala.reflect.Manifest[T]): IndexedSeq[T] = {
    toks.map(tok => (tok \ field).extract[T]).toIndexedSeq
  }

  def extractHeuristic(spanIndices: IndexedSeq[Array[Int]],
                       toks: List[JValue], govObj: String,
                       lemmas: Map[Int, String]): Map[Array[Int], Int] = {

    val hr = toks.map(tok => tok \ "heuristic_relation")
    val pairs = for (i <- spanIndices.indices) yield {
      val id = spanIndices(i)
      val tok = hr(i)
      val govObjId =
        if (tok == JNothing) -1
        else if ((tok \ govObj) == JNothing) -1
        else (tok \ govObj).extract[Int] - 1

      if (govObjId >= 0) {
        val lemma = (tok \ (govObj + "lemma")).extract[String]
        if (lemma != lemmas(govObjId))
          throw new Exception(s"Invalid heuristic for ${lemmas.mkString(" ")} at i")
      }

      id -> govObjId

    }

    pairs.toMap

  }

  def makeView(a: Annotation, viewName: ViewName,
               spanIndices: IndexedSeq[Array[Int]],
               labels: IndexedSeq[String]): View = {
    assert(spanIndices.length == labels.length)

    val (spans, attribs) = (for {
      i <- spanIndices.indices
      if labels(i) != null
    } yield {
      val s = spanIndices(i)
      val label = labels(i)

      val left = s.min
      val right = s.max + 1

      val span = Span(left, right)

      val attrib = Map("tokens" -> s.mkString(","))

      (span -> label, span -> attrib)

    }).unzip

    new SpanLabelView(viewName, a, spans, attribs)
  }

  def makeDependencyView(a: Annotation, heads: IndexedSeq[Int], deprels: IndexedSeq[String]) = {
    val constituents = (0 until a.size).map(i => a.makeDummyConstituent(i, i + 1))

    val relations = for {
      targetId <- 0 until a.size
      srcId = heads(targetId)
      if srcId >= 0
      deprel = deprels(targetId)

      src = constituents(srcId)
      tgt = constituents(targetId)
    } yield new Relation(src, tgt, deprel)

    new ForestView(ViewName.GoldDependencies, a, constituents, relations)
  }


  def makeCandidatePrepositionView(a: Annotation,
                                   viewName: ViewName,
                                   spanIndices: IndexedSeq[Array[Int]],
                                   lexCats: IndexedSeq[String],
                                   governors: Map[Array[Int], Int],
                                   objects: Map[Array[Int], Int]) = {
    assert(spanIndices.length == lexCats.length)

    val (cs, rs) = (for {
      i <- spanIndices.indices
//      if labels(i) != null
//      if labels(i).startsWith("p")
      if Set("P", "PP", "INF.P", "POSS", "PRON.POSS").contains(lexCats(i))
    } yield {
      val s = spanIndices(i)

      val left = s.min
      val right = s.max + 1

      val gov = governors(s)
      val obj = objects(s)

      val c = a.makeDummyConstituent(left, right).emptyClone(viewName, "Preposition")
      c.attributes("tokens") = s.mkString(",")

      val g = a.makeDummyConstituent(gov, gov + 1).emptyClone(viewName, "Governor")
      val o = a.makeDummyConstituent(obj, obj + 1).emptyClone(viewName, "Object")

      val r1 = new Relation(g, c, "gov")
      val r2 = new Relation(c, o, "obj")

      (Seq(c, g, o), Seq(r1, r2))

    }).unzip

    new ForestView(viewName, a, cs.flatten, rs.flatten)
  }

  def readSourceFromFile(file: File): Seq[Annotation] = {

    log info s"Reading $file"

    val lines: String = IOUtils.readLines(file, gzip = false).mkString("\n")

    log info s"Found ${lines.length} lines in the file"

    val json = parse(lines)

    val JArray(list) = json
    list.toSeq.map { item =>
      val id = (item \ "streusle_sent_id").extract[String]
      val rawText = (item \ "text").extract[String]

      val JArray(toks) = item \ "toks"

      val tokens = extract[String](toks, "word")
      val lemmas = extract[String](toks, "lemma").zipWithIndex.map(pair => pair._2 -> pair._1)
      val upos = extract[String](toks, "upos").zipWithIndex.map(pair => pair._2 -> pair._1)
      val xpos = extract[String](toks, "xpos").zipWithIndex.map(pair => pair._2 -> pair._1)
      val heads = extract[Int](toks, "head").map(_ - 1)
      val deprels = extract[String](toks, "deprel")


      val a = Annotation(id, rawText, IndexedSeq(tokens), tokenizerName = StandardAnnotators.Gold)
      a += new TokenLabelView(ViewName.GoldPOS, a, xpos)
      a += new TokenLabelView(ViewName.GoldLemma, a, lemmas)
      a += new TokenLabelView(ViewName.GoldUniversalPOS, a, upos)
      a += makeDependencyView(a, heads, deprels)



      val (sweId, swes) = (item \ "swes").extract[Map[String, JValue]].unzip
      val sLexLemmas = extract[String](swes.toList, "lexlemma")
      val sLexCats = extract[String](swes.toList, "lexcat")
      val sScene = extract[String](swes.toList, "ss")
      val sFunction = extract[String](swes.toList, "ss2")
      val sweToks = extract[Array[Int]](swes.toList, "toknums").map(_.map(_ - 1))
      val sGovernors = extractHeuristic(sweToks, swes.toList, "gov", lemmas.toMap)
      val sObjects = extractHeuristic(sweToks, swes.toList, "obj", lemmas.toMap)


      val (smweId, smwes: Iterable[JValue]) = (item \ "smwes").extract[Map[String, JValue]].unzip
      val smLexLemmas = extract[String](smwes.toList, "lexlemma")
      val smLexCats = extract[String](smwes.toList, "lexcat")
      val smScene = extract[String](smwes.toList, "ss")
      val smFunction = extract[String](smwes.toList, "ss2")
      val smweToks = extract[Array[Int]](smwes.toList, "toknums").map(_.map(_ - 1))

      val smweGovernors = extractHeuristic(smweToks, smwes.toList, "gov", lemmas.toMap)
      val smweObjects = extractHeuristic(smweToks, smwes.toList, "obj", lemmas.toMap)


      val lexLemmas = sLexLemmas ++ smLexLemmas
      val lexCats = sLexCats ++ smLexCats
      val scenes: IndexedSeq[String] = sScene ++ smScene
      val functions = sFunction ++ smFunction
      val spans: IndexedSeq[Array[Int]] = sweToks ++ smweToks
      val governors = sGovernors ++ smweGovernors
      val objects = sObjects ++ smweObjects

      a += makeCandidatePrepositionView(a, SSTReader.prepositions, spans, lexCats, governors, objects)
      a += makeView(a, SSTReader.lexCatViewName, spans, lexCats)
      a += makeView(a, SSTReader.lexLemmaView, spans, lexLemmas)
      a += makeView(a, SSTReader.sceneView, spans, scenes)
      a += makeView(a, SSTReader.functionView, spans, functions)

      a
    }

  }
}

object SSTReader {
  val prepositions = ViewName("Prepositions", StandardAnnotators.Gold)
  val lexCatViewName = ViewName("LexCat", StandardAnnotators.Gold)
  val sceneView = ViewName("Scene", StandardAnnotators.Gold)
  val functionView = ViewName("Function", StandardAnnotators.Gold)
  val lexLemmaView = ViewName("LexLemma", StandardAnnotators.Gold)
}
