package com.github.katjahahn.tools

import java.io.FileInputStream
import com.github.katjahahn.PEData
import java.io.File
import java.util.Random
import ShannonEntropy._
import com.github.katjahahn.sections.SectionLoader
import com.github.katjahahn.PELoader

/**
 * Tool to calculate Shannon's Entropy for entire files, byte arrays or sections of a PE
 *
 * @author Katja Hahn
 */
class ShannonEntropy(private val data: PEData) {

  /**
   * Calculates the entropy for the section with the sectionNumber
   *
   * @param sectioNumber number of the section
   * @return entropy of the section
   */
  def sectionEntropy(sectionNumber: Int): Double = {
    val bytesAndOffset = new SectionLoader(data).loadSectionBytes(sectionNumber)
    entropy(bytesAndOffset.bytes)
  }

  /**
   * Calculates the entropy for all sections of the file
   *
   * @return map with section number as keys and entropy as values
   */
  def sectionEntropies(): Map[Int, Double] = {
    val sectionNr = data.getCOFFFileHeader().getNumberOfSections()
    (for (i <- 1 to sectionNr) yield (i, sectionEntropy(i))) toMap
  }
}

object ShannonEntropy {

  private val chunkSize = 1024
  private val byteSize = 256

  def main(args: Array[String]): Unit = {
    val file = new File("WinRar.exe")
    val data = PELoader.loadPE(file)
    val ent = new ShannonEntropy(data)
    ent.sectionEntropies.foreach(println)
    println(data.getSectionTable().getInfo())
  }

  /**
   * Calculates Shannon's Entropy for the byte array
   *
   * @param bytes the input array
   * @return Shannon's Entropy for the byte array
   */
  def entropy(bytes: Array[Byte]): Double = {
    val (byteCounts, total) = countBytes(bytes)
    entropy(byteCounts, total)
  }

  /**
   * Calculates Shannon's Entropy for the file
   *
   * @param the file to calculate the entropy from
   * @return Shannon's Entropy for the file
   */
  def entropy(file: File): Double = {
    val (byteCounts, total) = countBytes(file)
    entropy(byteCounts, total)
  }

  private def entropy(byteCounts: Array[Long], total: Long): Double = {
    var entropy: Double = 0.0
    List.fromArray(byteCounts).foreach { counter =>
      if (counter != 0) {
        val p: Double = 1.0 * counter / total
        entropy -= p * (math.log(p) / math.log(byteSize))
      }
    }
    entropy
  }

  private def countBytes(bytes: Array[Byte]): (Array[Long], Long) = {
    val byteCounts = Array.fill[Long](byteSize)(0L)
    var total: Long = 0L
    List.fromArray(bytes).foreach { byte =>
      val index = (byte & 0xff)
      byteCounts(index) += 1L
      total += 1L
    }
    (byteCounts, total)
  }

  private def countBytes(file: File): (Array[Long], Long) = {
    using(new FileInputStream(file)) { fis =>
      val bytes = Array.fill[Byte](chunkSize)(0)
      val byteCounts = Array.fill[Long](byteSize)(0L)
      var total: Long = 0L
      Iterator
        .continually(fis.read(bytes))
        .takeWhile(-1 !=)
        .foreach { _ =>
          List.fromArray(bytes).foreach { byte =>
            byteCounts((byte & 0xff)) += 1
            total += 1
          }
        }
      (byteCounts, total)
    }
  }

  private def using[A <: { def close(): Unit }, B](param: A)(f: A => B): B =
    try { f(param) } finally { param.close() }

}