ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.1"

ThisBuild / dynverSeparator := "-"

Test / parallelExecution := false
Test / testOptions += Tests.Argument("-oDF")
Test / logBuffered := false

run / fork := true

// This will ensure that whenever sbt commands is run, the local1.conf/local2.conf ... configuration is picked up.
//run / javaOptions ++= sys.props.get("config.resource").map(res => s"-Dconfig.resource=$res").toSeq

// ctrl-c
Global / cancelable := false


lazy val LogbackVersion = "1.5.6"
lazy val ScalaVersion = "3.3.1"
lazy val AkkaVersion = "2.9.3"
lazy val ScalaTest = "3.2.18"
lazy val AlpakkaMQTT = "7.0.2"
lazy val SlickVersion = "3.5.1"
lazy val PostgreSQL = "42.7.3"
lazy val HTTPVersion = "10.6.3"
lazy val AkkaManagementVersion = "1.5.1"
lazy val ScalikeJdbcVersion = "4.3.1"
lazy val AkkaProjectionVersion = "1.5.0"
lazy val circeVersion = "0.14.9"

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

lazy val root = (project in file("."))
  .enablePlugins(AkkaGrpcPlugin, Cinnamon)
  .settings(
    name := "tars",
    libraryDependencies ++= Seq(

      Cinnamon.library.cinnamonAkka,
      Cinnamon.library.cinnamonAkkaHttp,
      Cinnamon.library.cinnamonJvmMetricsProducer,
      Cinnamon.library.cinnamonOpenTelemetry,

      // processing json
      "org.scala-lang" %% "toolkit" % "0.1.7",

      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "org.scalatest" %% "scalatest" % ScalaTest % Test,

      // MQTT
      "com.lightbend.akka" %% "akka-stream-alpakka-mqtt" % AlpakkaMQTT,


      // Serialization
      "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,

      // CQRS
      "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test,
      "com.lightbend.akka" %% "akka-persistence-jdbc" % "5.4.0",
      "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,

      "org.postgresql" % "postgresql" % PostgreSQL,
      "org.scalikejdbc" %% "scalikejdbc" % ScalikeJdbcVersion,
      "org.scalikejdbc" %% "scalikejdbc-test"   % ScalikeJdbcVersion   % Test,
      "org.scalikejdbc" %% "scalikejdbc-config" % ScalikeJdbcVersion,

      "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
      "com.lightbend.akka" %% "akka-projection-core" % AkkaProjectionVersion,
      "com.lightbend.akka" %% "akka-projection-eventsourced" % AkkaProjectionVersion,
      "com.lightbend.akka" %% "akka-projection-jdbc" % AkkaProjectionVersion,
      "com.lightbend.akka" %% "akka-projection-testkit" % AkkaProjectionVersion % Test,

      // HTTP
      "com.typesafe.akka" %% "akka-http" % HTTPVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-http2-support" % "10.5.3",
      "com.typesafe.akka" %% "akka-http-spray-json" % HTTPVersion,
      "com.lightbend.akka.management" %% "akka-management" % AkkaManagementVersion,
      "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
      "com.lightbend.akka.management" %% "akka-management-cluster-http" % AkkaManagementVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding" % AkkaVersion,
      "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,

      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,

      "com.lightbend.akka" %% "akka-stream-alpakka-influxdb" % "8.0.0",

    )


  )

cinnamonSuppressRepoWarnings := true
// Add the Cinnamon Agent for run and test
run / cinnamon := true
test / cinnamon := true
// Set the Cinnamon Agent log level
cinnamonLogLevel := "INFO"
