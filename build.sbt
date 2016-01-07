name := "WSDB"

version := "1.0"

lazy val `wsdb` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies ++= Seq(
	jdbc,
	cache,
	ws,
	"mysql" % "mysql-connector-java" % "5.1.38",
	"com.typesafe.play" %% "play-slick" % "1.1.1",
	"com.adrianhurt" %% "play-bootstrap" % "1.0-P24-B3-SNAPSHOT" exclude("org.webjars", "jquery") exclude("org.webjars", "bootstrap"),
	"org.scalatest" %% "scalatest" % "2.2.5" % "test",
	"org.scalatestplus" %% "play" % "1.4.0-M3" % "test"
)

scalacOptions ++= Seq("-feature")

routesGenerator := InjectedRoutesGenerator
