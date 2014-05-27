name := "PortEx"

version := "0.4.0"

javadocSettings

libraryDependencies += "org.testng" % "testng" % "6.8.8" % "test"

libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % "2.0-rc1"

libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % "2.0-rc1"

resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"
