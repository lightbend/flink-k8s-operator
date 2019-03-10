resolvers += "Bintray Repository" at "https://dl.bintray.com/shmishleniy/"

resolvers += "JAnalyse Repository" at "http://www.janalyse.fr/repository/"

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.5")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.4")
addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.5.0")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2")