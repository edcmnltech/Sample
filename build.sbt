scalaVersion := "2.13.1"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.6.3"
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.6.3"
libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.1.11"
libraryDependencies += "com.typesafe" % "config" % "1.4.0"
libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % "3.3.2",
  "org.slf4j" % "slf4j-nop" % "1.6.4",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.3.2"
)

val circeVersion = "0.12.3"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

libraryDependencies += "de.heikoseeberger" %% "akka-http-circe" % "1.31.0"

libraryDependencies += "com.typesafe.akka" %% "akka-stream-testkit" % "2.6.3" % Test
libraryDependencies += "mysql" % "mysql-connector-java" % "8.0.19" % "runtime"