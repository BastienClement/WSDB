name := "WSDB"

version := "1.0"

lazy val `wsdb` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
	jdbc,
	cache,
	ws,
	"mysql" % "mysql-connector-java" % "5.1.38",
	"com.typesafe.play" %% "play-slick" % "1.1.1"
)

libraryDependencies ++= Seq(
	"org.scalatest" %% "scalatest" % "2.2.5" % "test",
	"org.scalatestplus" %% "play" % "1.4.0-M3" % "test"
)

scalacOptions ++= Seq("-feature")

routesGenerator := InjectedRoutesGenerator
