import AssemblyKeys._ // put this at the top of the file

assemblySettings

jarName in assembly := "portex.jar"

test in assembly := {}

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case x if x.endsWith(".exe") => MergeStrategy.discard
    case PathList("reports", xs @ _*) => MergeStrategy.discard
    case PathList("testfiles", xs @ _*) => MergeStrategy.discard
    case x => old(x)
  }
}

//mainClass in assembly := Some("com.github.katjahahn.tools.Jar2ExeScanner")
