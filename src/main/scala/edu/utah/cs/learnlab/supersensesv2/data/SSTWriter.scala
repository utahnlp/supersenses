package edu.utah.cs.learnlab.supersensesv2.data

import java.io.{File, PrintWriter}

import edu.utah.cs.learnlab.wordstock.core.{Annotation, _}
import edu.utah.cs.learnlab.wordstock.utilities.Navigators._
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._

/**
  * @author Vivek Srikumar
  */
class SSTWriter(file: File,
                predictedSceneView: ViewName,
                predictedFunctionView: ViewName) {

  private implicit val formats = DefaultFormats

  val out = new PrintWriter(file)

  private val array = new collection.mutable.ArrayBuffer[Annotation]()

  def write(a: Annotation) = {
    array += a
  }

  def close() = {

    val as = array.toIndexedSeq.map(makeJson)

    out.println(pretty(render(as)))

    out.close()
  }

  def makeJson(a: Annotation): JObject = {

    val id = a.id
    val sentId = {
      val parts = id.replace("ewtb.r.", "reviews-").split("\\.")
      val p2 = ("000" + parts(1)).takeRight(4)
      parts(0) + "-" + p2
    }
    val rawText = a.rawText
    val tokenizedSentences = a.tokenizedSentences

    val spans = a(SSTReader.prepositions).constituents.filter(_.label == "Preposition")

    val swe = spans.filter(_.length == 1)
    val smwe = spans.filter(_.length != 1)

    val toks = makeToks(a)
    val swes = makeSwes(a, swe)
    val swmwes = makeSwes(a, smwe)

    ("sent_id" -> sentId) ~
      ("streusle_sent_id" -> id) ~
      ("text" -> rawText) ~
      ("toks" -> toks) ~
      ("swes" -> swes) ~
      ("smwes" -> swmwes)

  }

  def makeSwes(a: Annotation, spans: IndexedSeq[Constituent]): JObject = {
    val lexlemmaView = a(SSTReader.lexLemmaView)
    val lexcatView = a(SSTReader.lexCatViewName)
    val scene = a(predictedSceneView)
    val function = a(predictedFunctionView)

    var res: JObject = null

    for {
      (c, i) <- spans.zipWithIndex
      lexlemma = c.labelInView(SSTReader.lexLemmaView).orNull
      lexcat = c.labelInView(SSTReader.lexCatViewName).orNull
      if lexcat != null
      if  Set("P", "PP", "INF.P", "POSS", "PRON.POSS").contains(lexcat)
    } yield {
      val scene = c.labelInView(predictedSceneView).orNull
      val function = c.labelInView(predictedFunctionView).orNull
      val tokens = c.attributes("tokens").split(",").map(_.toInt + 1).toSeq

      val json = ("lexlemma" -> lexlemma) ~
        ("lexcat" -> lexcat) ~
        ("ss" -> scene) ~
        ("ss2" -> function) ~
        ("toknums" -> tokens)

      if (res == null) res = (i + 1).toString -> json else res = res ~ ((i + 1).toString -> json)
    }

    if (res == null) Map[String, String]() else res
  }

  def makeToks(a: Annotation) =
    for (tok <- a(ViewName.GoldUniversalPOS).constituents) yield {
      val token = a.tokens(tok.span.start)
      val lemma = tok.lemma(ViewName.GoldLemma)
      val upos = tok.label
      val xpos = tok.pos(ViewName.GoldPOS)
      val (head, deprel) = tok.incomingDependencyRelation(ViewName.GoldDependencies) match {
        case None => (0, "root")
        case Some(r) => (r.source.span.start + 1, r.label)
      }

      ("#" -> (tok.span.start + 1)) ~
        ("word" -> token) ~
        ("lemma" -> lemma) ~
        ("upos" -> upos) ~
        ("xpos" -> xpos) ~
        ("head" -> head) ~
        ("deprel" -> deprel)
    }
}
