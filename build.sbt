name := "supersenses"

version := "1.0"

scalaVersion := "2.11.7"

retrieveManaged := true

resolvers ++= Seq(
  "CogcompSoftware" at "http://cogcomp.cs.illinois.edu/m2repo/"
)

libraryDependencies ++= Seq(
  "com.github.scopt" %% "scopt" % "3.3.0",
  "com.jsuereth" %% "scala-arm" % "1.4",
  "com.typesafe" % "config" % "1.3.0",
  "com.typesafe.akka" %% "akka-actor" % "2.3.10",
  "edu.illinois.cs.cogcomp" % "cogcomp-common-resources" % "1.2",
  "edu.illinois.cs.cogcomp" % "edison" % "0.7.4",
  "edu.mit" % "jwi" % "2.2.3",
  "edu.stanford.nlp" % "stanford-corenlp" % "3.5.2" classifier "models",
  "edu.stanford.nlp" % "stanford-corenlp" % "3.5.2",
  "net.sf.trove4j" % "trove4j" % "3.0.3",
  "org.apache.commons" % "commons-compress" % "1.12",
  "org.json4s" %% "json4s-native" % "3.2.10",
  "org.slf4j" % "slf4j-nop" % "1.6.4"
)
