name := """bolero-server"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala, JavaServerAppPackaging)

scalaVersion := "2.11.6"

resolvers ++= Seq(
  Resolver.jcenterRepo,
  Resolver.bintrayRepo("scalaz", "releases")
)

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "javax.inject" % "javax.inject" % "1",
  "org.reactivemongo" %% "play2-reactivemongo" % "0.11.7.play24",
  "com.github.athieriot" %% "specs2-embedmongo" % "0.7.0",
  "ch.qos.logback" % "logback-classic" % "1.1.3"
)

scalacOptions += "-feature"

scalacOptions ++= Seq("-Xmax-classfile-name", "100")

// https://github.com/playframework/playframework/issues/3017
scalacOptions ++= Seq("-encoding", "UTF-8")

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator

// 生产环境配置
javaOptions in Universal ++= Seq(
  // "-Dconfig.resource=release.conf"

  // Since play uses separate pidfile we have to provide it with a proper path
  s"-Dpidfile.path=/var/run/${packageName.value}/play.pid",

  // Use separate configuration file for production environment
  s"-Dconfig.file=/usr/share/${packageName.value}/conf/application.conf",

  s"-Dhttp.port=9000"

  // Use separate logger configuration file for production environment
  // s"-Dlogger.file=/usr/share/${packageName.value}/conf/production-logger.xml",
)

maintainer in Linux := "Scott LIU <scozv@yandex.com>"

packageSummary in Linux := "RESTful API of Bolero Server"

packageDescription := ""
