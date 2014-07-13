/**
 * *****************************************************************************
 * Copyright 2014 Katja Hahn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package com.github.katjahahn.tools.anomalies

import java.io.File
import scala.PartialFunction._
import scala.collection.JavaConverters._
import com.github.katjahahn.parser.IOUtil._
import com.github.katjahahn.tools.Overlay
import com.github.katjahahn.parser.sections.SectionLoader
import com.github.katjahahn.parser.PELoader
import com.github.katjahahn.parser.PEData
import com.github.katjahahn.tools.Visualizer
import javax.imageio.ImageIO

/**
 * Scans for anomalies and malformations in a PE file.
 *
 * @author Katja Hahn
 */
class PEAnomalyScanner(data: PEData) extends AnomalyScanner(data) {

  /**
   * Scans the PE and returns a report of the anomalies found.
   *
   * @return a description string of the scan
   */
  override def scanReport: String = {
    val report = StringBuilder.newBuilder
    report ++= "Scanned File: " + data.getFile.getName + NL
    for (anomaly <- scan()) {
      report ++= "\t* " + anomaly.description + NL
    }
    report.toString
  }

  /**
   * Scans the PE and returns a (scala-)list of the anomalies found.
   * Returns an empty list if no traits have been added.
   *
   * Use getAnomalies for a Java compatible list.
   *
   * @return (scala-)list of anomalies found
   */
  override def scan: List[Anomaly] = {
    List[Anomaly]()
  }

  /**
   * @return list of anomalies found
   */
  def getAnomalies: java.util.List[Anomaly] = scan.asJava

}

object PEAnomalyScanner {

  /**
   * Parses the given file and creates a PEAnomalyScanner instance that has all scanning
   *  characteristics applied.
   *
   * @param file the pe file to scan for
   * @return a PEAnomalyScanner instance with the traits applied from the boolean values
   */
  def newInstance(file: File): PEAnomalyScanner = {
    val data = PELoader.loadPE(file)
    newInstance(data)
  }

  /**
   * Creates a PEAnomalyScanner instance that has all scanning characteristics
   * applied.
   *
   * @param data the PEData object created by the PELoader
   * @return a PEAnomalyScanner instance with the traits applied from the boolean values
   */
  def newInstance(data: PEData): PEAnomalyScanner =
    new PEAnomalyScanner(data) with COFFHeaderScanning with OptionalHeaderScanning with SectionTableScanning with MSDOSHeaderScanning with ImportSectionScanning with ExportSectionScanning with ResourceSectionScanning

  def main(args: Array[String]): Unit = {
    val folder = new File("/home/deque/portextestfiles/badfiles/")
    var counter = 0
    for (file <- folder.listFiles()) {
      val outfile = new File("peimages/" + file.getName() + ".png")
      counter += 1
      if (counter % 1000 == 0) {
        println("files read: " + counter)
      }
      if (!outfile.exists()) {
        try {
          val data = PELoader.loadPE(file)
          //      println(data)
          val loader = new SectionLoader(data)
          val scanner = PEAnomalyScanner.newInstance(data)
          val over = new Overlay(data)
          //        if (!scanner.getAnomalies.asScala.filter(a => a.subtype == AnomalySubType.FRACTIONATED_DATADIR).isEmpty) {
          println(scanner.scanReport)
          println("has overlay: " + over.exists())
          println("overlay offset: " + over.getOffset() + " (0x" + java.lang.Long.toHexString(over.getOffset()) + ")")
          //          println(file.getName())
          println("file size: " + file.length() + " (0x" + java.lang.Long.toHexString(file.length) + ")")
          val vi = new Visualizer(data)
          val image = vi.createEntropyImage()
          ImageIO.write(image, "png", outfile);
          println()

          //        }
        } catch {
          case e: Exception => System.err.println(e.getMessage)
        }
      }
    }
  }

}
