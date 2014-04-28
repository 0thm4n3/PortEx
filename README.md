PortEx
======

### Welcome to PortEx

PortEx is a Java library for static malware analysis of portable executable files.  
PortEx is written in Java and Scala, but targeted for Java applications.  
Visit the [PortEx project page](http://katjahahn.github.io/PortEx/).

### Features (so far)

* Reading Header information from: MSDOS Header, COFF File Header, Optional Header, Section Table
* Dumping of: MSDOS Load Module, Sections, Overlay, embedded ZIP, JAR or .class files
* Mapping of Data Directory Entries to the corresponding Section
* Reading Standard Section Formats: Import Section, Resource Section, Export Section, Debug Section
* Scan for PEiD userdb signatures
* Scan for jar2exe or class2exe wrappers
* Scan for Unicode and ASCII strings contained in the file
* Get a Virustotal report

For more information have a look at [PortEx Wiki](https://github.com/katjahahn/PortEx/wiki) and the [Documentation](http://katjahahn.github.io/PortEx/javadocs/)

### Version Information

The current version is in Alpha, so beware of bugs.
The first release will be in Fall 2014.

### Using PortEx

Download portex.jar and include it to your build path. For more information, read the [PortEx Wiki](https://github.com/katjahahn/PortEx/wiki)

### Building PortEx

#### Requirements

PortEx is build with [sbt](http://www.scala-sbt.org)  
You also need [Maven](https://maven.apache.org/)

#### Setup Third Party Libraries

Download [VirusTotalPublic](https://github.com/kdkanishka/Virustotal-Public-API-V2.0-Client/archive/master.zip)

Extract the file and navigate to the *Virustotal-Public-API-V2.0-Client-master* folder. Build the jar with:

```
$ mvn clean install -DskipTests
```

Then publish it to your local Maven repository:

```
$ mvn install:install-file -Dfile=target/VirustotalPublicV2.0.0-1.1-GA.jar -DpomFile=pom.xml
```

#### Compile and Build With sbt

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

#### Create Eclipse Project

You can create an eclipse project by using the sbteclipse plugin.
Add the following line to *project/plugins.sbt*:

```
addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.4.0")
```

Generate the project files for Eclipse:

```
$ sbt eclipse
```

Import the project to Eclipse via the *Import Wizard*.

### Author and Contact
Katja Hahn  
E-Mail: portx (at) gmx (dot) de

### License
[Apache License, Version 2.0](https://github.com/katjahahn/PortEx/blob/master/LICENSE)
