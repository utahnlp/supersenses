package edu.utah.cs.learnlab.supersensesv2.data

import edu.utah.cs.learnlab.core.features._
import edu.utah.cs.learnlab.logging.HasLogging
import edu.utah.cs.learnlab.utilities.{IOUtils, LearnlabConfig}
import edu.utah.cs.learnlab.wordstock.utilities.Tree
import java.io.File
import org.json4s._
import org.json4s.native.JsonMethods._
import scala.annotation.tailrec
import scala.collection.mutable

/**
  * @author Vivek Srikumar
  */
case class SupersenseHierarchy(hierarchyFile: File) extends HasLogging {
  private implicit val formats = DefaultFormats

  val (data, labelToHierarchyMap, supersenses) = read(hierarchyFile)

  lazy val labelLexicon: Lexicon = {
    val llex = new Lexicon()
    withTimer("Populating label lexicon") {
      supersenses.foreach(l => llex(l))
    }
    llex.lockLexicon()
    llex
  }
  

  def read(file: File): (Tree[String], Map[String, Tree[String]], Set[String]) = {
    log info s"Reading the supersense hierarchy from $file"

    val lines = IOUtils.readLines(file, gzip = false).mkString("\n")
    val json = parse(lines)
    val map = new mutable.HashMap[String, Tree[String]]()
    val labels = new mutable.HashSet[String]

    val t = makeTree("Root", json, map, labels)

    (t, map.toMap, labels.toSet)
  }


  def makeTree(label: String, j: JValue, map: mutable.HashMap[String, Tree[String]],
    labels: mutable.HashSet[String]): Tree[String] = {
    val JObject(list) = j
    val children = list.map {
      item: JField =>
      makeTree(item._1, item._2, map, labels)
    }

    val t = Tree(label, children)
    map(label) = t

    if (label != "Root")
      labels += label
    t
  }

  def parent(label: String) = {
    labelToHierarchyMap(label).parent.map(_.label).filter(s => s != "Root")
  }

  def hierarchyRoot(label: String) = {
    val tree = labelToHierarchyMap(label)

    @tailrec def helper(t: Tree[String], soFar: List[String]): List[String] = {
      t.parent match {
        case None => soFar
        case Some(parent) => helper(parent, parent.label :: soFar)
      }
    }

    val pathToRoot = helper(tree, tree.label :: Nil)
    val root = pathToRoot.tail.head
    if(root == "Root") label else root
  }
}
