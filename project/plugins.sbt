resolvers += Resolver.url(
  "jetbrains-bintray",
  url("https://dl.bintray.com/jetbrains/sbt-plugins/")
)(Resolver.ivyStylePatterns)

addSbtPlugin("org.jetbrains" % "sbt-idea-plugin" % "3.6.5")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.3.2")
