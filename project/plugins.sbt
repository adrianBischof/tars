resolvers += "Akka library repository".at("https://repo.akka.io/maven")
// for building gRPC servers and clients on top of Akka Streams and Akka HTTP
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.4.3")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.16")
addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.0.1")
addSbtPlugin("com.lightbend.cinnamon" % "sbt-cinnamon" % "2.20.1")
