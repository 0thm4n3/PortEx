PortEx
======

### Welcome to PortEx

PortEx is a Java library for static malware analysis of portable executable files.
PortEx is written in Java and Scala, but targeted for Java applications.

### Features (so far)

* Reading Header information from: MSDOS Header, COFF File Header, Optional Header, Section Table
* Dumping of: MSDOS Load Module, Sections, Overlay, embedded ZIP, JAR or .class files
* Mapping of Data Directory Entries to the corresponding Section
* Reading Standard Section Formats: Import Section, Resource Section
* Scan for PEiD userdb signatures
* Scan for jar2exe or class2exe wrappers

For more information have a look at [PortEx Wiki](https://github.com/katjahahn/PortEx/wiki/Getting-Started) and the [Documentation](http://katjahahn.github.io/PortEx/javadocs/)

### Version Information

The current version is not even Alpha yet, which is the reason that there are no binaries provided by now. However you can build the current source.

### Build

PortEx is build with [sbt](http://www.scala-sbt.org)

To simply compile the project invoke:

```
$ sbt compile
```

To create a jar: 

```
$ sbt package
```

For a fat jar (not recommended):

```
$ sbt assembly
```

### Create Eclipse Project

Add the following line to project/plugins.sbt

```
addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.4.0")
```

Generate the project files for Eclipse:

```
$ sbt eclipse
```

Import the project to Eclipse via the Import Wizard.

### Author
[Katja Hahn](http://katjahahn.github.io/)

### License
[BSD 2-Clause License](https://github.com/katjahahn/PortEx/blob/master/LICENSE)
