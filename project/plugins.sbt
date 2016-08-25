logLevel := Level.Warn

resolvers += Resolver.url("dancingrobot84-bintray",
  url("http://dl.bintray.com/dancingrobot84/sbt-plugins/"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.3")
addSbtPlugin("com.dancingrobot84" % "sbt-idea-plugin" % "0.4.0")
