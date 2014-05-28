package com.github.katjahahn.sections.debug

import com.github.katjahahn.PEModule
import com.github.katjahahn.IOUtil
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import com.github.katjahahn.StandardField
import com.github.katjahahn.ByteArrayUtil._
import DebugSection._
import com.github.katjahahn.PELoader
import java.io.File
import com.github.katjahahn.HeaderKey
import com.github.katjahahn.sections.SectionLoader
import DebugDirTableKey._
import java.util.Date
import com.github.katjahahn.sections.SpecialSection
import com.github.katjahahn.PEData

/**
 * @author Katja Hahn
 *
 * Represents the debug section of the PE.
 */
class DebugSection private (
  private val directoryTable: DebugDirectoryTable,
  private val typeDescription: String,
  val offset: Long) extends SpecialSection {
  
  override def getOffset(): Long = offset

  override def getInfo(): String =
    s"""|-------------
        |Debug Section
        |-------------
        |
        |${
      directoryTable.values.map(s => s.key match {
        case TYPE => "Type: " + typeDescription
        case TIME_DATE_STAMP => "Time date stamp: " + getTimeDateStamp().toString
        case _ => s.toString
      }).mkString(PEModule.NL)
    }""".stripMargin

  /**
   * Returns a date object of the time date stamp in the debug section.
   *
   * @return date of the time date stamp
   */
  def getTimeDateStamp(): Date = new Date(get(TIME_DATE_STAMP) * 1000)

  /**
   * Returns a long value of the given key or null if the value doesn't exist.
   *
   * @param key the header key
   * @return long value for the given key of null if it doesn't exist.
   */
  def get(key: HeaderKey): java.lang.Long =
    if (directoryTable.contains(key))
      directoryTable(key).value else null

  /**
   * Returns a string of the type description
   * 
   * @return type description string
   */
  def getTypeDescription(): String = typeDescription

}

object DebugSection {

  type DebugDirectoryTable = Map[HeaderKey, StandardField]

  private val debugspec = "debugdirentryspec"

  def main(args: Array[String]): Unit = {
    val file = new File("src/main/resources/testfiles/ntdll.dll")
    val data = PELoader.loadPE(file)
    val loader = new SectionLoader(data)
    val debug = loader.loadDebugSection()
    println(debug.getInfo())
  }
  
  /**
   * Creates an instance of the DebugSection for the given debug bytes.
   * 
   * @param debugbytes the byte array that represents the debug section
   * @param offset the debug sections starts at
   * @return debugsection instance
   */
  def getInstance(debugbytes: Array[Byte], offset: Long): DebugSection = apply(debugbytes, offset)
  
  /**
   * Loads the debug section and returns it.
   * 
   * This is just a shortcut to loading the section using the {@link SectionLoader}
   * 
   * @return instance of the debug section
   */
  def load(data: PEData): DebugSection = 
    new SectionLoader(data).loadDebugSection()

  def apply(debugbytes: Array[Byte], offset: Long): DebugSection = {
    val specification = IOUtil.readMap("debugdirentryspec").asScala.toMap
    val buffer = ListBuffer.empty[StandardField]
    for ((key, specs) <- specification) {
      val description = specs(0)
      val offset = Integer.parseInt(specs(1))
      val size = Integer.parseInt(specs(2))
      val value = getBytesLongValue(debugbytes.clone, offset, size)
      val ekey = DebugDirTableKey.valueOf(key)
      val entry = new StandardField(ekey, description, value)
      buffer += entry
    }
    val entries: DebugDirectoryTable = (buffer map { t => (t.key, t) }).toMap;
    val types = IOUtil.getCharacteristicsDescriptions(entries(DebugDirTableKey.TYPE).value, "debugtypes").asScala.toList
    new DebugSection(entries, types(0), offset)
  }
}